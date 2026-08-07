package cloud.zhixuyun.student;

import cloud.zhixuyun.ai.AiAssistantClient;
import cloud.zhixuyun.ai.AiGradingService;
import cloud.zhixuyun.auth.AuthException;
import cloud.zhixuyun.auth.AuthSessionService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.storage.ResourceStorage;
import cloud.zhixuyun.workflow.LearningWorkflowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {
    private static final String STUDENT_COURSE_ACCESS_SQL = """
            (c.class_name=ac.name
             or ce.id is not null
             or exists (
                 select 1
                 from teaching_class visible_tc
                 where visible_tc.course_id=c.id
                   and visible_tc.enabled=true
                   and (visible_tc.administrative_class_id=sp.administrative_class_id
                        or (visible_tc.administrative_class_id is null and visible_tc.name=ac.name))
             ))
            """;
    private static final String STUDENT_ASSIGNED_TEACHER_ID_SQL = """
            coalesce((
                select visible_tcta.teacher_id
                from teaching_class visible_tc
                join teaching_class_teacher_assignment visible_tcta on visible_tcta.teaching_class_id=visible_tc.id
                where visible_tc.course_id=c.id
                  and visible_tc.enabled=true
                  and (visible_tc.administrative_class_id=sp.administrative_class_id
                       or (visible_tc.administrative_class_id is null and visible_tc.name=ac.name))
                order by visible_tc.id
                limit 1
            ), c.teacher_id)
            """;

    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final AiAssistantClient ai;
    private final AiGradingService grading;
    private final SubmissionTextExtractor textExtractor;
    private final LearningWorkflowService workflow;
    private final ResourceStorage resourceStorage;

    public StudentService(JdbcTemplate jdbc, AuthSessionService sessions, ObjectMapper json, AiAssistantClient ai,
                          AiGradingService grading, SubmissionTextExtractor textExtractor, LearningWorkflowService workflow,
                          ResourceStorage resourceStorage) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.json = json;
        this.ai = ai;
        this.grading = grading;
        this.textExtractor = textExtractor;
        this.workflow = workflow;
        this.resourceStorage = resourceStorage;
    }

    public UserAccount requireStudent(String authorization) {
        UserAccount user = sessions.requireUser(authorization);
        if (user.getRole() != Role.STUDENT) {
            throw new AuthException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "仅学生账号可以访问此功能");
        }
        return user;
    }

    public Map<String, Object> workspace(UserAccount student) {
        Map<String, Object> profile = profile(student);
        String studentNo = String.valueOf(profile.get("studentNo"));
        List<Map<String, Object>> courses = courses(student.getId());
        List<Map<String, Object>> tasks = tasks(studentNo, courses);
        List<Map<String, Object>> reports = reports(tasks);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("metrics", metrics(tasks));
        result.put("courses", courses);
        result.put("tasks", tasks);
        result.put("reports", reports);
        result.put("notifications", notifications(student, tasks));
        result.put("assistantSessions", assistantSessions(student.getId()));
        result.put("conversations", conversations(student.getId()));
        result.put("teacherContacts", teacherContacts(student));
        result.put("assistantPrompts", List.of(
                "帮我总结待完成实验任务",
                "根据 AI 初评给我一份修改建议",
                "告诉我最近一周最需要优先完成的任务"));
        return result;
    }

    @Transactional
    public Map<String, Object> submitText(UserAccount student, long taskId, String content) {
        if (content == null || content.isBlank()) throw badRequest("请先填写实验报告内容");
        StudentContext context = context(student, taskId);
        ensureSubmissionAllowed(context);
        String submissionText = content.trim();
        textExtractor.validateLength(submissionText);
        upsertSubmission(context, submissionText, null, grade(context, submissionText));
        return workspace(student);
    }

    @Transactional
    public Map<String, Object> submitFile(UserAccount student, long taskId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("请选择要上传的报告文件");
        StudentContext context = context(student, taskId);
        ensureSubmissionAllowed(context);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "FILE");
        payload.put("fileName", safeFilename(file.getOriginalFilename()));
        payload.put("contentType", file.getContentType());
        payload.put("size", file.getSize());
        String submissionText = textExtractor.extract(file);
        upsertSubmission(context, submissionText, writeJson(payload), grade(context, submissionText));
        return workspace(student);
    }

    @Transactional
    public Map<String, Object> submitAnswers(UserAccount student, long taskId, Object rawAnswers) {
        StudentContext context = context(student, taskId);
        ensureSubmissionAllowed(context);
        Map<String, Object> answers = normalizeAnswers(rawAnswers);
        List<Map<String, Object>> questions = readJsonList(context.questionsJson());
        if (questions.isEmpty()) throw badRequest("当前作业没有可提交的题目");
        for (Map<String, Object> question : questions) {
            String id = String.valueOf(question.get("id"));
            Object answer = answers.get(id);
            if (answer == null || (answer instanceof String text && text.isBlank())
                    || (answer instanceof List<?> values && values.isEmpty())) {
                throw badRequest("请完成全部题目后再提交");
            }
        }
        String submissionText = writeJson(answers);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "ANSWERS");
        payload.put("answers", answers);
        upsertSubmission(context, submissionText, writeJson(payload), grade(context, submissionText));
        return workspace(student);
    }

    public Download downloadResource(UserAccount student, long resourceId) {
        String sql = """
                select r.name,r.content_type,r.storage_key,r.content
                from course_resource r
                join course c on c.id=r.course_id
                join student_profile sp on sp.user_id=?
                left join administrative_class ac on ac.id=sp.administrative_class_id
                left join course_enrollment ce on ce.course_id=c.id and ce.student_id=sp.user_id and ce.active=true
                where r.id=? and
                """ + STUDENT_COURSE_ACCESS_SQL;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) throw notFound("资料不存在或无权访问");
            String storageKey = rs.getString("storage_key");
            byte[] content;
            try {
                content = storageKey == null || storageKey.isBlank() ? rs.getBytes("content") : resourceStorage.read(storageKey);
            } catch (IOException exception) {
                throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_ERROR", "课程资料读取失败");
            }
            return new Download(rs.getString("name"), rs.getString("content_type"), content == null ? new byte[0] : content);
        }, student.getId(), resourceId);
    }

    public Map<String, Object> askAssistant(UserAccount student, String content) {
        return askAssistantWithAi(student, content);
    }

    @Transactional
    public Map<String, Object> createAssistantSession(UserAccount student) {
        Instant now = Instant.now();
        long id = insert("insert into student_assistant_session(student_id,title,created_at,updated_at) values (?,?,?,?)",
                student.getId(), "新对话", Timestamp.from(now), Timestamp.from(now));
        return assistantSession(id, student.getId());
    }

    @Transactional
    public Map<String, Object> sendAssistantMessage(UserAccount student, long sessionId, String content) {
        requireAssistantSession(student.getId(), sessionId);
        if (content == null || content.isBlank()) throw badRequest("消息不能为空");
        Instant now = Instant.now();
        jdbc.update("insert into student_assistant_message(session_id,role,content,created_at) values (?,?,?,?)",
                sessionId, "USER", content.trim(), Timestamp.from(now));
        String answer = studentAssistantAnswer(student, sessionId);
        jdbc.update("insert into student_assistant_message(session_id,role,content,created_at) values (?,?,?,?)",
                sessionId, "ASSISTANT", answer, Timestamp.from(now.plus(1, ChronoUnit.SECONDS)));
        String title = jdbc.queryForObject("select title from student_assistant_session where id=?", String.class, sessionId);
        jdbc.update("update student_assistant_session set title=?,updated_at=? where id=?",
                "新对话".equals(title) ? summarize(content) : title, Timestamp.from(now), sessionId);
        return assistantSession(sessionId, student.getId());
    }

    public List<Map<String, Object>> teacherContacts(UserAccount student) {
        String sql = """
                select distinct u.id,u.display_name,c.name course_name
                from course c
                join student_profile sp on sp.user_id=?
                left join administrative_class ac on ac.id=sp.administrative_class_id
                left join course_enrollment ce on ce.course_id=c.id and ce.student_id=sp.user_id and ce.active=true
                join user_account u on u.id=
                """ + STUDENT_ASSIGNED_TEACHER_ID_SQL + """
                 and u.enabled=true
                where
                """ + STUDENT_COURSE_ACCESS_SQL + """
                order by u.display_name,c.name
                """;
        return jdbc.query(sql, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("name", rs.getString("display_name"));
            value.put("courseName", rs.getString("course_name"));
            return value;
        }, student.getId());
    }

    @Transactional
    public Map<String, Object> createConversation(UserAccount student, long teacherId, String content) {
        if (teacherId <= 0) throw badRequest("请选择任课教师");
        if (content == null || content.isBlank()) throw badRequest("首条消息不能为空");
        boolean allowed = teacherContacts(student).stream()
                .anyMatch(item -> ((Number) item.get("id")).longValue() == teacherId);
        if (!allowed) throw notFound("教师不存在或不属于当前学生课程");
        List<Long> existing = jdbc.query("select id from conversation where teacher_id=? and student_id=? order by id desc limit 1",
                (rs, row) -> rs.getLong("id"), teacherId, student.getId());
        long conversationId;
        if (existing.isEmpty()) {
            String studentName = profile(student).get("displayName").toString();
            Instant now = Instant.now();
            conversationId = insert("insert into conversation(teacher_id,student_id,contact_name,contact_type,avatar_text,unread_count,student_unread_count,updated_at) values (?,?,?,?,?,?,?,?)",
                    teacherId, student.getId(), studentName, "STUDENT", studentName.substring(0, 1), 0, 0, Timestamp.from(now));
        } else {
            conversationId = existing.get(0);
        }
        jdbc.update("insert into conversation_message(conversation_id,sender,title,content,created_at) values (?,?,?,?,?)",
                conversationId, "STUDENT", null, content.trim(), Timestamp.from(Instant.now()));
        jdbc.update("update conversation set unread_count=unread_count+1,updated_at=? where id=?",
                Timestamp.from(Instant.now()), conversationId);
        return conversation(conversationId, student.getId());
    }

    @Transactional
    public Map<String, Object> sendConversationMessage(UserAccount student, long conversationId, String content) {
        conversation(conversationId, student.getId());
        if (content == null || content.isBlank()) throw badRequest("消息不能为空");
        jdbc.update("insert into conversation_message(conversation_id,sender,title,content,created_at) values (?,?,?,?,?)",
                conversationId, "STUDENT", null, content.trim(), Timestamp.from(Instant.now()));
        jdbc.update("update conversation set unread_count=unread_count+1,updated_at=? where id=?",
                Timestamp.from(Instant.now()), conversationId);
        return conversation(conversationId, student.getId());
    }

    @Transactional
    public Map<String, Object> readConversation(UserAccount student, long conversationId) {
        conversation(conversationId, student.getId());
        jdbc.update("update conversation set student_unread_count=0 where id=?", conversationId);
        return conversation(conversationId, student.getId());
    }

    private Map<String, Object> askAssistantWithAi(UserAccount student, String content) {
        if (content == null || content.isBlank()) throw badRequest("AI 问题不能为空");
        Map<String, Object> profile = profile(student);
        String studentNo = String.valueOf(profile.get("studentNo"));
        List<Map<String, Object>> tasks = tasks(studentNo, courses(student.getId()));
        String context = writeJson(Map.of("profile", profile, "tasks", tasks));
        String system = "你是知序云的学生学习助手。只基于提供的学生课程和任务数据回答，不要编造成绩、截止时间或政策。给出具体、可执行的学习建议；无法从数据确定时明确说明。回答使用简洁中文。当前学生数据：" + context;
        return Map.of("answer", ai.complete(system, List.of(Map.of("role", "user", "content", content.trim()))));
    }

    private Map<String, Object> askAssistantLegacy(UserAccount student, String content) {
        if (content == null || content.isBlank()) throw badRequest("请输入问题内容");
        Map<String, Object> profile = profile(student);
        String studentNo = String.valueOf(profile.get("studentNo"));
        List<Map<String, Object>> tasks = tasks(studentNo, courses(student.getId()));
        long pending = tasks.stream().filter(task -> "待提交".equals(task.get("submissionStatus"))).count();
        long reviewed = tasks.stream().filter(task -> "教师已复核".equals(task.get("submissionStatus"))).count();
        String prompt = content.trim();
        String answer;
        if (prompt.contains("待完成") || prompt.contains("优先")) {
            answer = "你当前还有 " + pending + " 项任务待完成。建议优先处理截止时间最近的任务，再回头根据 AI 初评优化已提交报告。";
        } else if (prompt.contains("AI") || prompt.contains("初评") || prompt.contains("修改")) {
            answer = "修改时可以优先补三类内容：实验原理是否说明充分、关键步骤是否完整、结果与误差分析是否有证据支撑。";
        } else if (prompt.contains("复核") || prompt.contains("发布")) {
            answer = "你当前已有 " + reviewed + " 项任务进入教师复核完成状态。可以先查看教师评语，再决定是否整理经验到下一份报告里。";
        } else {
            answer = "我可以基于你当前的课程、任务和提交状态给出建议。你可以继续问我待办排序、报告修改重点，或者某门课程的任务进度。";
        }
        return Map.of("answer", answer);
    }

    private Map<String, Object> profile(UserAccount student) {
        return jdbc.query("""
                select u.id,u.login_name,u.display_name,sp.student_no,sp.grade_year,ac.name class_name
                from user_account u
                join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                where u.id=?
                """, rs -> {
            if (!rs.next()) throw notFound("学生资料不存在");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("loginName", rs.getString("login_name"));
            value.put("displayName", rs.getString("display_name"));
            value.put("studentNo", rs.getString("student_no"));
            value.put("gradeYear", rs.getString("grade_year"));
            value.put("className", rs.getString("class_name"));
            return value;
        }, student.getId());
    }

    private List<Map<String, Object>> courses(long studentId) {
        String sql = """
                select distinct c.id,c.name,c.code,c.class_name,c.semester,c.schedule_text,c.color,u.display_name teacher_name
                from course c
                join student_profile sp on sp.user_id=?
                left join administrative_class ac on ac.id=sp.administrative_class_id
                left join course_enrollment ce on ce.course_id=c.id and ce.student_id=sp.user_id and ce.active=true
                join user_account u on u.id=
                """ + STUDENT_ASSIGNED_TEACHER_ID_SQL + """
                where
                """ + STUDENT_COURSE_ACCESS_SQL + """
                order by c.id
                """;
        List<Map<String, Object>> values = jdbc.query(sql, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("name", rs.getString("name"));
            value.put("code", rs.getString("code"));
            value.put("className", rs.getString("class_name"));
            value.put("semester", rs.getString("semester"));
            value.put("scheduleText", rs.getString("schedule_text"));
            value.put("teacherName", rs.getString("teacher_name"));
            value.put("color", rs.getString("color"));
            return value;
        }, studentId);
        values.forEach(course -> course.put("resources", courseResources(studentId, ((Number) course.get("id")).longValue())));
        return values;
    }

    private List<Map<String, Object>> courseResources(long studentId, long courseId) {
        String sql = """
                select r.id,r.kind,r.name,r.source_label,r.content_type,r.file_size,r.created_at
                from course_resource r
                join course c on c.id=r.course_id
                join student_profile sp on sp.user_id=?
                left join administrative_class ac on ac.id=sp.administrative_class_id
                left join course_enrollment ce on ce.course_id=c.id and ce.student_id=sp.user_id and ce.active=true
                where r.course_id=? and
                """ + STUDENT_COURSE_ACCESS_SQL + """
                order by r.created_at desc
                """;
        return jdbc.query(sql, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("kind", rs.getString("kind"));
            value.put("name", rs.getString("name"));
            value.put("sourceLabel", rs.getString("source_label"));
            value.put("contentType", rs.getString("content_type"));
            value.put("fileSize", rs.getObject("file_size"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant());
            return value;
        }, studentId, courseId);
    }

    private List<Map<String, Object>> tasks(String studentNo, List<Map<String, Object>> courses) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> course : courses) {
            long courseId = ((Number) course.get("id")).longValue();
            tasks.addAll(jdbc.query("""
                    select t.id,t.course_id,t.task_type,t.name,t.description,t.start_at,t.deadline,t.max_score,t.questions_json,
                           s.id submission_id,s.submitted_at,s.ai_score,s.teacher_score,s.ai_review,s.teacher_comment,s.report_text,s.answers_json,
                           s.review_status,s.current_version_no
                    from learning_task t
                    left join task_submission s on s.task_id=t.id and (s.student_no=? or replace(s.student_no,'20230','2023')=?)
                    where t.course_id=?
                    order by t.deadline asc,t.id desc
                    """, (rs, row) -> taskMap(rs), studentNo, studentNo, courseId));
        }
        return tasks;
    }

    private Map<String, Object> taskMap(ResultSet rs) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getLong("id"));
        value.put("courseId", rs.getLong("course_id"));
        value.put("taskType", rs.getString("task_type"));
        value.put("name", rs.getString("name"));
        value.put("description", rs.getString("description"));
        value.put("startAt", rs.getTimestamp("start_at").toInstant());
        value.put("deadline", rs.getTimestamp("deadline").toInstant());
        value.put("maxScore", rs.getInt("max_score"));
        value.put("questions", readJsonList(rs.getString("questions_json")));
        value.put("submissionId", rs.getObject("submission_id"));
        value.put("submittedAt", rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant());
        value.put("aiScore", (Integer) rs.getObject("ai_score"));
        value.put("teacherScore", (Integer) rs.getObject("teacher_score"));
        value.put("aiReview", rs.getString("ai_review"));
        value.put("teacherComment", rs.getString("teacher_comment"));
        value.put("reviewStatus", rs.getString("review_status"));
        value.put("currentVersionNo", rs.getInt("current_version_no"));
        value.put("reportText", rs.getString("report_text"));
        Map<String, Object> submissionPayload = readJsonObject(rs.getString("answers_json"));
        value.put("answers", submissionPayload.getOrDefault("answers", Map.of()));
        value.put("attachment", "FILE".equals(submissionPayload.get("mode")) ? submissionPayload : Map.of());
        value.put("submissionStatus", statusOf(rs.getObject("submission_id") != null, rs.getObject("teacher_score"), rs.getString("ai_review"), rs.getString("review_status")));
        return value;
    }

    private List<Map<String, Object>> reports(List<Map<String, Object>> tasks) {
        return tasks.stream().filter(task -> task.get("submissionId") != null).map(task -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("submissionId", task.get("submissionId"));
            value.put("taskId", task.get("id"));
            value.put("courseId", task.get("courseId"));
            value.put("taskName", task.get("name"));
            value.put("submittedAt", task.get("submittedAt"));
            value.put("submissionStatus", task.get("submissionStatus"));
            value.put("aiScore", task.get("aiScore"));
            value.put("teacherScore", task.get("teacherScore"));
            value.put("aiReview", task.get("aiReview"));
            value.put("teacherComment", task.get("teacherComment"));
            value.put("reviewStatus", task.get("reviewStatus"));
            value.put("currentVersionNo", task.get("currentVersionNo"));
            value.put("reportText", task.get("reportText"));
            value.put("attachment", task.get("attachment"));
            return value;
        }).sorted((left, right) -> compareInstant(right.get("submittedAt"), left.get("submittedAt"))).toList();
    }

    private Map<String, Object> metrics(List<Map<String, Object>> tasks) {
        long pending = tasks.stream().filter(task -> "待提交".equals(task.get("submissionStatus"))).count();
        long submitted = tasks.stream().filter(task -> task.get("submissionId") != null).count();
        long aiReady = tasks.stream().filter(task -> "AI 初评完成".equals(task.get("submissionStatus"))).count();
        long reviewed = tasks.stream().filter(task -> "教师已复核".equals(task.get("submissionStatus"))).count();
        return Map.of("pendingTaskCount", pending, "submittedCount", submitted, "aiReadyCount", aiReady, "reviewedCount", reviewed);
    }

    private List<Map<String, Object>> notifications(UserAccount student, List<Map<String, Object>> tasks) {
        List<Map<String, Object>> stored = workflow.notifications(student);
        List<Map<String, Object>> notices = new ArrayList<>(stored);
        for (Map<String, Object> task : tasks) {
            String status = String.valueOf(task.get("submissionStatus"));
            if ("待提交".equals(status)) {
                notices.add(notification("TASK", "待提交任务提醒", task.get("name") + " 尚未提交，请在截止前完成。", ((Instant) task.get("deadline")).minus(1, ChronoUnit.DAYS), "TODO"));
            } else if ("AI 初评完成".equals(status)) {
                notices.add(notification("AI", "AI 初评已生成", task.get("name") + " 已生成 AI 初评，可先查看修改方向。", valueInstant(task.get("submittedAt")), "INFO"));
            } else if ("教师已复核".equals(status)) {
                notices.add(notification("REVIEW", "教师最终评价已发布", task.get("name") + " 已发布教师最终评价，请及时查看。", valueInstant(task.get("submittedAt")), "DONE"));
            }
        }
        return notices.stream().sorted((left, right) -> compareInstant(right.get("createdAt"), left.get("createdAt"))).limit(100).toList();
    }

    private Map<String, Object> notification(String type, String title, String content, Instant createdAt, String status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", createdAt.toEpochMilli() + title.hashCode());
        value.put("type", type);
        value.put("title", title);
        value.put("content", content);
        value.put("createdAt", createdAt);
        value.put("status", status);
        return value;
    }

    private List<Map<String, Object>> assistantSessions(long studentId) {
        return jdbc.query("select id from student_assistant_session where student_id=? order by updated_at desc",
                (rs, row) -> assistantSession(rs.getLong("id"), studentId), studentId);
    }

    private Map<String, Object> assistantSession(long id, long studentId) {
        return jdbc.query("select id,title,created_at,updated_at from student_assistant_session where id=? and student_id=?", rs -> {
            if (!rs.next()) throw notFound("AI 对话不存在或无权访问");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("title", rs.getString("title"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant());
            value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
            value.put("messages", jdbc.query("select id,role,content,created_at from student_assistant_message where session_id=? order by created_at,id",
                    (messages, row) -> {
                        Map<String, Object> message = new LinkedHashMap<>();
                        message.put("id", messages.getLong("id"));
                        message.put("role", messages.getString("role"));
                        message.put("content", messages.getString("content"));
                        message.put("createdAt", messages.getTimestamp("created_at").toInstant());
                        return message;
                    }, id));
            return value;
        }, id, studentId);
    }

    private String studentAssistantAnswer(UserAccount student, long sessionId) {
        Map<String, Object> profile = profile(student);
        List<Map<String, Object>> tasks = tasks(String.valueOf(profile.get("studentNo")),
                courses(student.getId()));
        List<Map<String, String>> messages = jdbc.query(
                "select role,content from student_assistant_message where session_id=? order by created_at,id",
                (rs, row) -> Map.of("role", "ASSISTANT".equals(rs.getString("role")) ? "assistant" : "user",
                        "content", rs.getString("content")), sessionId);
        String system = "你是知序云的学生学习助手。只基于当前学生授权的课程和任务回答，不编造成绩、截止时间或学校政策。"
                + "优先给出简洁、可执行的学习建议；信息不足时明确说明。当前学生数据："
                + writeJson(Map.of("profile", profile, "tasks", tasks));
        return ai.complete(system, messages);
    }

    private void requireAssistantSession(long studentId, long sessionId) {
        Integer count = jdbc.queryForObject("select count(*) from student_assistant_session where id=? and student_id=?",
                Integer.class, sessionId, studentId);
        if (count == null || count == 0) throw notFound("AI 对话不存在或无权访问");
    }

    private List<Map<String, Object>> conversations(long studentId) {
        return jdbc.query("select id from conversation where student_id=? order by updated_at desc",
                (rs, row) -> conversation(rs.getLong("id"), studentId), studentId);
    }

    private Map<String, Object> conversation(long id, long studentId) {
        return jdbc.query("""
                select c.id,c.teacher_id,u.display_name contact_name,c.student_unread_count,c.updated_at
                from conversation c join user_account u on u.id=c.teacher_id
                where c.id=? and c.student_id=?
                """, rs -> {
            if (!rs.next()) throw notFound("会话不存在或无权访问");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("teacherId", rs.getLong("teacher_id"));
            value.put("contactName", rs.getString("contact_name"));
            value.put("avatarText", rs.getString("contact_name").substring(0, 1));
            value.put("unreadCount", rs.getInt("student_unread_count"));
            value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
            value.put("messages", jdbc.query("select id,sender,title,content,created_at from conversation_message where conversation_id=? order by created_at,id",
                    (messages, row) -> {
                        Map<String, Object> message = new LinkedHashMap<>();
                        message.put("id", messages.getLong("id"));
                        message.put("sender", messages.getString("sender"));
                        message.put("title", messages.getString("title"));
                        message.put("content", messages.getString("content"));
                        message.put("createdAt", messages.getTimestamp("created_at").toInstant());
                        return message;
                    }, id));
            return value;
        }, id, studentId);
    }

    private StudentContext context(UserAccount student, long taskId) {
        String sql = """
                select t.id task_id,t.task_type,t.name task_name,t.description,t.max_score,t.questions_json,
                       t.start_at,t.deadline,sp.student_no,u.display_name
                from learning_task t
                join student_profile sp on sp.user_id=?
                join user_account u on u.id=sp.user_id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                join course c on c.id=t.course_id
                left join course_enrollment ce on ce.course_id=c.id and ce.student_id=sp.user_id and ce.active=true
                where t.id=? and
                """ + STUDENT_COURSE_ACCESS_SQL;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) throw notFound("任务不存在或不属于当前学生");
            return new StudentContext(
                    rs.getLong("task_id"),
                    rs.getString("task_type"),
                    rs.getString("task_name"),
                    rs.getString("description"),
                    rs.getInt("max_score"),
                    rs.getString("questions_json"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("deadline").toInstant(),
                    rs.getString("student_no"),
                    rs.getString("display_name"));
        }, student.getId(), taskId);
    }

    private void ensureSubmissionAllowed(StudentContext context) {
        List<Map<String, Object>> reviewed = jdbc.query("""
                select teacher_score,review_status
                from task_submission
                where task_id=? and (student_no=? or replace(student_no,'20230','2023')=?)
                order by id desc
                limit 1
                """, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("teacherScore", rs.getObject("teacher_score"));
            value.put("reviewStatus", rs.getString("review_status"));
            return value;
        }, context.taskId(), context.studentNo(), context.studentNo());
        boolean returned = !reviewed.isEmpty() && "RETURNED".equals(reviewed.get(0).get("reviewStatus"));

        Instant now = Instant.now();
        if (now.isBefore(context.startAt())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TASK_NOT_STARTED", "任务尚未开始，暂不能提交");
        }
        if (now.isAfter(context.deadline()) && !returned) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TASK_CLOSED", "任务已截止，暂不能提交");
        }
        if (!reviewed.isEmpty() && !returned) {
            Map<String, Object> current = reviewed.get(0);
            if (current.get("teacherScore") != null || "PUBLISHED".equals(current.get("reviewStatus"))) {
                throw new AuthException(
                        HttpStatus.CONFLICT,
                        "REPORT_ALREADY_REVIEWED",
                        "教师已完成评分，如需修改请联系教师退回任务");
            }
            throw new AuthException(
                    HttpStatus.CONFLICT,
                    "SUBMISSION_ALREADY_EXISTS",
                    "任务已提交，如需修改请联系教师退回重交");
        }
    }

    private AiGradingService.GradeResult grade(StudentContext context, String submissionText) {
        return grading.grade(new AiGradingService.GradeRequest(
                context.taskType(), context.taskName(), context.description(), context.maxScore(),
                context.questionsJson(), submissionText));
    }

    private void upsertSubmission(StudentContext context, String reportText, String attachmentJson,
                                  AiGradingService.GradeResult grade) {
        List<Long> existing = jdbc.query("""
                select id from task_submission
                where task_id=? and (student_no=? or replace(student_no,'20230','2023')=?)
                order by id desc limit 1
                """, (rs, row) -> rs.getLong("id"), context.taskId(), context.studentNo(), context.studentNo());
        long submissionId;
        if (!existing.isEmpty()) {
            submissionId = existing.get(0);
            jdbc.update("""
                    update task_submission
                    set student_name=?,student_no=?,submitted=true,submitted_at=?,ai_score=?,teacher_score=null,answers_json=?,report_text=?,ai_review=?,teacher_comment=null,
                        review_status='SUBMITTED',current_version_no=current_version_no+1
                    where id=?
                    """, context.displayName(), context.studentNo(), Timestamp.from(Instant.now()), grade.score(), attachmentJson == null ? "[]" : attachmentJson,
                    reportText, grade.review(), submissionId);
        } else {
            jdbc.update("""
                    insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,answers_json,report_text,ai_review,teacher_comment,review_status,current_version_no)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, context.taskId(), context.displayName(), context.studentNo(), true, Timestamp.from(Instant.now()), grade.score(), null,
                    attachmentJson == null ? "[]" : attachmentJson, reportText, grade.review(), null, "SUBMITTED", 1);
            submissionId = jdbc.queryForObject("select max(id) from task_submission where task_id=? and student_no=?", Long.class, context.taskId(), context.studentNo());
        }
        Integer version = jdbc.queryForObject("select current_version_no from task_submission where id=?", Integer.class, submissionId);
        jdbc.update("insert into submission_version(submission_id,version_no,report_text,attachment_json,ai_score,ai_review,created_at) values (?,?,?,?,?,?,?)",
                submissionId, version == null ? 1 : version, reportText, attachmentJson == null ? "[]" : attachmentJson,
                grade.score(), grade.review(), Timestamp.from(Instant.now()));
    }

    private Map<String, Object> readJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            Object value = json.readValue(raw, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                return normalized;
            }
        } catch (JsonProcessingException ignored) {
        }
        return Map.of();
    }

    private List<Map<String, Object>> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Object value = json.readValue(raw, Object.class);
            if (value instanceof List<?> list) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        map.forEach((key, entry) -> row.put(String.valueOf(key), entry));
                        normalized.add(row);
                    }
                }
                return normalized;
            }
        } catch (JsonProcessingException ignored) {
        }
        return List.of();
    }

    private Map<String, Object> normalizeAnswers(Object rawAnswers) {
        if (!(rawAnswers instanceof Map<?, ?> values)) throw badRequest("答题内容格式不正确");
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw badRequest("提交内容序列化失败");
        }
    }

    private long insert(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("数据库未返回新增记录 ID");
        return keys.getKey().longValue();
    }

    private static String summarize(String content) {
        String value = content == null ? "新对话" : content.trim().replaceAll("\\s+", " ");
        return value.length() <= 18 ? value : value.substring(0, 18) + "…";
    }

    private static String statusOf(boolean submitted, Object teacherScore, String aiReview, String reviewStatus) {
        if (!submitted) return "待提交";
        if ("RETURNED".equals(reviewStatus)) return "已退回";
        if (teacherScore != null) return "教师已复核";
        if (aiReview != null && !aiReview.isBlank()) return "AI 初评完成";
        return "已提交";
    }

    private static int compareInstant(Object left, Object right) {
        return valueInstant(left).compareTo(valueInstant(right));
    }

    private static Instant valueInstant(Object value) {
        return value instanceof Instant instant ? instant : Instant.EPOCH;
    }

    private static String safeFilename(String name) {
        return name == null || name.isBlank() ? "未命名文件" : name.replace("\\", "_").replace("/", "_");
    }

    private static AuthException badRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private static AuthException notFound(String message) {
        return new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private record StudentContext(long taskId, String taskType, String taskName, String description,
                                  int maxScore, String questionsJson, Instant startAt, Instant deadline,
                                   String studentNo, String displayName) {}

    public record Download(String filename, String contentType, byte[] content) {}
}
