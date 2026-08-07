package cloud.zhixuyun.teacher;

import cloud.zhixuyun.ai.AiAssistantClient;
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
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeacherService {
    private static final String COURSE_ACCESS_SQL = """
            (c.teacher_id=? or exists (
                select 1 from course_teacher_assignment cta
                where cta.course_id=c.id and cta.teacher_id=?
            ))
            """;

    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final AiAssistantClient ai;
    private final ResourceStorage resourceStorage;
    private final LearningWorkflowService workflow;

    public TeacherService(JdbcTemplate jdbc, AuthSessionService sessions, ObjectMapper json, ResourceStorage resourceStorage,
                          AiAssistantClient ai, LearningWorkflowService workflow) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.json = json;
        this.ai = ai;
        this.resourceStorage = resourceStorage;
        this.workflow = workflow;
    }

    public UserAccount requireTeacher(String authorization) {
        UserAccount user = sessions.requireUser(authorization);
        if (user.getRole() != Role.TEACHER) {
            throw new AuthException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "仅教师账号可以访问此功能");
        }
        return user;
    }

    public Map<String, Object> workspace(UserAccount teacher) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile(teacher));
        List<Map<String, Object>> courses = courses(teacher.getId());
        result.put("courses", courses);
        result.put("semesters", courses.stream().map(row -> row.get("semester")).distinct().toList());
        result.put("metrics", metrics(teacher.getId(), courses));
        result.put("alerts", alerts(teacher.getId()));
        result.put("assistantSessions", assistantSessions(teacher.getId()));
        result.put("conversations", conversations(teacher.getId()));
        result.put("recommendations", List.of(
                "分析实验 6 的共性问题", "生成事务传播机制练习题", "查看需要重点关注的学生", "整理本周课程教学小结"));
        return result;
    }

    public Map<String, Object> profile(UserAccount teacher) {
        return jdbc.query("select department,title,email,phone,bio from teacher_profile where user_id=?", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", teacher.getId());
            row.put("loginName", teacher.getLoginName());
            row.put("displayName", teacher.getDisplayName());
            if (rs.next()) {
                row.put("department", rs.getString("department"));
                row.put("title", rs.getString("title"));
                row.put("email", rs.getString("email"));
                row.put("phone", rs.getString("phone"));
                row.put("bio", rs.getString("bio"));
            }
            return row;
        }, teacher.getId());
    }

    @Transactional
    public Map<String, Object> updateProfile(UserAccount teacher, Map<String, Object> body) {
        String displayName = text(body, "displayName", teacher.getDisplayName());
        jdbc.update("update user_account set display_name=? where id=?", displayName, teacher.getId());
        Object[] values = { text(body, "department", "软件工程系"), text(body, "title", "教师"),
                text(body, "email", ""), text(body, "phone", ""), text(body, "bio", ""), teacher.getId() };
        int changed = jdbc.update("update teacher_profile set department=?,title=?,email=?,phone=?,bio=? where user_id=?", values);
        if (changed == 0) {
            jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                    teacher.getId(), values[0], values[1], values[2], values[3], values[4]);
        }
        teacher.setDisplayName(displayName);
        return profile(teacher);
    }

    @Transactional
    public Map<String, Object> addResource(UserAccount teacher, long courseId, String kind, boolean shared, MultipartFile file) {
        requireCourse(teacher.getId(), courseId);
        if (file == null || file.isEmpty()) throw badRequest("请选择要上传的文件");
        try {
            ResourceStorage.StoredResource stored = resourceStorage.store("teacher-resources", safeFilename(file.getOriginalFilename()),
                    file.getContentType(), file.getBytes());
            long id = insert("insert into course_resource(course_id,owner_id,kind,name,source_label,shared,content_type,storage_backend,storage_key,file_size,content,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    courseId, teacher.getId(), kind == null ? "MATERIAL" : kind, safeFilename(file.getOriginalFilename()),
                    shared ? "本人共享" : "本人私有", shared, file.getContentType(), stored.storageBackend(),
                    stored.storageKey(), stored.fileSize(), null, Timestamp.from(Instant.now()));
            return resource(id, teacher.getId());
        } catch (IOException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "文件读取失败");
        }
    }

    public Download downloadResource(UserAccount teacher, long resourceId) {
        return jdbc.query("select r.name,r.content_type,r.storage_key,r.content from course_resource r join course c on c.id=r.course_id where r.id=? and " + COURSE_ACCESS_SQL,
                rs -> {
                    if (!rs.next()) throw notFound("资料不存在或无权访问");
                    String storageKey = rs.getString("storage_key");
                    byte[] content = (storageKey == null || storageKey.isBlank()) ? rs.getBytes("content") : readStoredResource(storageKey);
                    return new Download(rs.getString("name"), rs.getString("content_type"), content);
                }, resourceId, teacher.getId(), teacher.getId());
    }

    @Transactional
    public void deleteResource(UserAccount teacher, long resourceId) {
        String storageKey = jdbc.query("select r.storage_key from course_resource r join course c on c.id=r.course_id "
                        + "where r.id=? and r.owner_id=? and " + COURSE_ACCESS_SQL,
                rs -> {
                    if (!rs.next()) throw notFound("璧勬枃涓嶅瓨鍦ㄣ€佸苟闈炴湰浜轰笂浼犳垨鏃犳潈鍒犻櫎");
                    return rs.getString("storage_key");
                }, resourceId, teacher.getId(), teacher.getId(), teacher.getId());
        deleteStoredResource(storageKey);
        int changed = jdbc.update("delete from course_resource where id=?", resourceId);
        if (changed == 0) throw notFound("资料不存在、并非本人上传或无权删除");
    }

    @Transactional
    public Map<String, Object> createTask(UserAccount teacher, long courseId, Map<String, Object> body) {
        requireCourse(teacher.getId(), courseId);
        String type = text(body, "taskType", "HOMEWORK").toUpperCase();
        if (!List.of("HOMEWORK", "EXPERIMENT").contains(type)) throw badRequest("任务类型无效");
        Instant start = instant(body.get("startAt"), Instant.parse("2099-01-01T00:00:00Z"));
        Instant deadline = instant(body.get("deadline"), Instant.parse("2099-12-31T23:59:59Z"));
        if (deadline.isBefore(start)) throw badRequest("截止时间不能早于开始时间");
        String questions = writeJson(body.getOrDefault("questions", List.of()));
        long id = insert("insert into learning_task(course_id,task_type,name,description,start_at,deadline,max_score,questions_json,created_at) values (?,?,?,?,?,?,?,?,?)",
                courseId, type, text(body, "name", defaultTaskName(type)), text(body, "description", ""), Timestamp.from(start),
                Timestamp.from(deadline), integer(body.get("maxScore"), 100), questions, Timestamp.from(Instant.now()));
        workflow.notifyCourseStudents(courseId, "发布了新的实验任务", text(body, "name", defaultTaskName(type)) + " 已发布，请及时查看要求与截止时间。", "TASK", id);
        return task(id, teacher.getId());
    }

    @Transactional
    public Map<String, Object> updateTask(UserAccount teacher, long taskId, Map<String, Object> body) {
        requireTask(teacher.getId(), taskId);
        Instant start = instant(body.get("startAt"), Instant.parse("2099-01-01T00:00:00Z"));
        Instant deadline = instant(body.get("deadline"), Instant.parse("2099-12-31T23:59:59Z"));
        if (deadline.isBefore(start)) throw badRequest("截止时间不能早于开始时间");
        jdbc.update("update learning_task set name=?,description=?,start_at=?,deadline=?,max_score=? where id=?",
                text(body, "name", "未命名任务"), text(body, "description", ""), Timestamp.from(start), Timestamp.from(deadline),
                integer(body.get("maxScore"), 100), taskId);
        return task(taskId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> updateQuestions(UserAccount teacher, long taskId, Object questions) {
        requireTask(teacher.getId(), taskId);
        jdbc.update("update learning_task set questions_json=? where id=?", writeJson(questions), taskId);
        return task(taskId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> gradeSubmission(UserAccount teacher, long submissionId, Map<String, Object> body) {
        Map<String, Object> current = jdbc.query("select s.task_id,t.max_score from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where s.id=? and " + COURSE_ACCESS_SQL,
                rs -> {
                    if (!rs.next()) throw notFound("提交不存在或无权批改");
                    return Map.of("taskId", rs.getLong("task_id"), "maxScore", rs.getInt("max_score"));
                }, submissionId, teacher.getId(), teacher.getId());
        int score = integer(body.get("teacherScore"), -1);
        int max = (int) current.get("maxScore");
        if (score < 0 || score > max) throw badRequest("评分必须在 0 到满分之间");
        jdbc.update("update task_submission set teacher_score=?,teacher_comment=?,review_status='PUBLISHED' where id=?",
                score, text(body, "teacherComment", ""), submissionId);
        workflow.notifySubmissionStudent(submissionId, "教师评价已发布", "最终成绩：" + score + " 分。" + text(body, "teacherComment", ""));
        return submission(submissionId);
    }

    @Transactional
    public Map<String, Object> markAlertRead(UserAccount teacher, long alertId) {
        int changed = jdbc.update("update teaching_alert set status=case when status='UNREAD' then 'READ' else status end where id=? and teacher_id=?",
                alertId, teacher.getId());
        if (changed == 0) throw notFound("预警不存在");
        return alert(alertId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> saveProposal(UserAccount teacher, long alertId, String proposal) {
        if (proposal == null || proposal.isBlank()) throw badRequest("请填写解决方案");
        int changed = jdbc.update("update teaching_alert set proposal=?,status='PROPOSED' where id=? and teacher_id=?",
                proposal.trim(), alertId, teacher.getId());
        if (changed == 0) throw notFound("预警不存在");
        return alert(alertId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> newAssistantSession(UserAccount teacher) {
        long id = insert("insert into assistant_session(teacher_id,title,created_at,updated_at) values (?,?,?,?)",
                teacher.getId(), "新对话", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return session(id, teacher.getId());
    }

    @Transactional
    public Map<String, Object> sendAssistantMessage(UserAccount teacher, long sessionId, String content) {
        requireSession(teacher.getId(), sessionId);
        if (content == null || content.isBlank()) throw badRequest("消息不能为空");
        Instant now = Instant.now();
        jdbc.update("insert into assistant_message(session_id,role,content,created_at) values (?,?,?,?)",
                sessionId, "USER", content.trim(), Timestamp.from(now));
        String answer = assistantAnswerWithAi(teacher.getId(), sessionId, content.trim());
        jdbc.update("insert into assistant_message(session_id,role,content,created_at) values (?,?,?,?)",
                sessionId, "ASSISTANT", answer, Timestamp.from(now.plus(1, ChronoUnit.SECONDS)));
        String currentTitle = jdbc.queryForObject("select title from assistant_session where id=?", String.class, sessionId);
        if ("新对话".equals(currentTitle)) {
            jdbc.update("update assistant_session set title=?,updated_at=? where id=?", summarize(content), Timestamp.from(now), sessionId);
        } else {
            jdbc.update("update assistant_session set updated_at=? where id=?", Timestamp.from(now), sessionId);
        }
        return session(sessionId, teacher.getId());
    }

    public Map<String, Object> contactCandidates(UserAccount teacher, long courseId, String query, int page, int size) {
        requireCourse(teacher.getId(), courseId);
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        String keyword = query == null ? "" : query.trim();
        String pattern = "%" + keyword + "%";
        Integer total = jdbc.queryForObject("select count(distinct sp.user_id) from student_profile sp join user_account u on u.id=sp.user_id "
                + "left join administrative_class ac on ac.id=sp.administrative_class_id "
                + "left join course_enrollment ce on ce.student_id=sp.user_id and ce.course_id=? and ce.active=true "
                + "where (ac.name=(select class_name from course where id=?) or ce.id is not null) "
                + "and (u.display_name like ? or sp.student_no like ?)", Integer.class,
                courseId, courseId, pattern, pattern);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> items = jdbc.query("select distinct u.id,u.display_name,sp.student_no,coalesce(ac.name,c.class_name) class_name "
                        + "from student_profile sp join user_account u on u.id=sp.user_id "
                        + "left join administrative_class ac on ac.id=sp.administrative_class_id "
                        + "left join course_enrollment ce on ce.student_id=sp.user_id and ce.course_id=? and ce.active=true "
                        + "join course c on c.id=? "
                        + "where (ac.name=c.class_name or ce.id is not null) "
                        + "and (u.display_name like ? or sp.student_no like ?) "
                        + "order by sp.student_no limit ? offset ?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id")); item.put("name", rs.getString("display_name"));
                    item.put("studentNo", rs.getString("student_no")); item.put("className", rs.getString("class_name"));
                    return item;
                }, courseId, courseId, pattern, pattern, safeSize, offset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items); result.put("page", safePage); result.put("size", safeSize);
        result.put("total", total == null ? 0 : total); result.put("pages", total == null || total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        return result;
    }

    @Transactional
    public Map<String, Object> createConversation(UserAccount teacher, long studentId, String content) {
        if (content == null || content.isBlank()) throw badRequest("首条消息不能为空");
        Map<String, Object> student = jdbc.query("select u.id,u.display_name,sp.student_no from student_profile sp "
                        + "join user_account u on u.id=sp.user_id left join administrative_class ac on ac.id=sp.administrative_class_id "
                        + "where u.id=? and (exists (select 1 from course c where " + COURSE_ACCESS_SQL + " and c.class_name=ac.name) "
                        + "or exists (select 1 from course c join course_enrollment ce on ce.course_id=c.id "
                        + "where " + COURSE_ACCESS_SQL + " and ce.student_id=sp.user_id and ce.active=true))",
                rs -> {
                    if (!rs.next()) throw notFound("学生不在当前任课班级中");
                    return Map.of("id", rs.getLong("id"), "name", rs.getString("display_name"), "studentNo", rs.getString("student_no"));
                }, studentId, teacher.getId(), teacher.getId(), teacher.getId(), teacher.getId());
        Integer existing = jdbc.queryForObject("select count(*) from conversation where teacher_id=? and student_id=?", Integer.class,
                teacher.getId(), studentId);
        if (existing != null && existing > 0) throw badRequest("该学生已经存在会话");
        Instant now = Instant.now();
        long conversationId = insert("insert into conversation(teacher_id,student_id,contact_name,contact_type,avatar_text,unread_count,student_unread_count,updated_at) values (?,?,?,?,?,?,?,?)",
                teacher.getId(), studentId, student.get("name"), "STUDENT", String.valueOf(student.get("name")).substring(0, 1), 0, 1, Timestamp.from(now));
        jdbc.update("insert into conversation_message(conversation_id,sender,title,content,created_at) values (?,?,?,?,?)",
                conversationId, "TEACHER", null, content.trim(), Timestamp.from(now));
        return conversation(conversationId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> sendConversationMessage(UserAccount teacher, long conversationId, String content) {
        Map<String, Object> conversation = conversation(conversationId, teacher.getId());
        if ("SYSTEM".equals(conversation.get("contactType"))) throw badRequest("系统通知为只读会话");
        if (content == null || content.isBlank()) throw badRequest("消息不能为空");
        jdbc.update("insert into conversation_message(conversation_id,sender,title,content,created_at) values (?,?,?,?,?)",
                conversationId, "TEACHER", null, content.trim(), Timestamp.from(Instant.now()));
        jdbc.update("update conversation set unread_count=0,student_unread_count=student_unread_count+1,updated_at=? where id=?", Timestamp.from(Instant.now()), conversationId);
        return conversation(conversationId, teacher.getId());
    }

    @Transactional
    public Map<String, Object> readConversation(UserAccount teacher, long conversationId) {
        conversation(conversationId, teacher.getId());
        jdbc.update("update conversation set unread_count=0 where id=?", conversationId);
        return conversation(conversationId, teacher.getId());
    }

    private List<Map<String, Object>> courses(long teacherId) {
        return jdbc.query("select c.id,c.name,c.code,c.class_name,c.semester,c.schedule_text,c.student_count,c.color "
                        + "from course c where " + COURSE_ACCESS_SQL + " order by c.id",
                (rs, row) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    long id = rs.getLong("id");
                    value.put("id", id);
                    value.put("name", rs.getString("name"));
                    value.put("code", rs.getString("code"));
                    value.put("className", rs.getString("class_name"));
                    value.put("semester", rs.getString("semester"));
                    value.put("scheduleText", rs.getString("schedule_text"));
                    value.put("studentCount", rs.getInt("student_count"));
                    value.put("color", rs.getString("color"));
                    value.put("resources", resources(id, teacherId));
                    value.put("tasks", tasks(id, teacherId));
                    return value;
                }, teacherId, teacherId);
    }

    private List<Map<String, Object>> resources(long courseId, long teacherId) {
        return jdbc.query("select r.id,r.kind,r.name,r.source_label,r.shared,r.content_type,r.owner_id,r.created_at from course_resource r join course c on c.id=r.course_id where r.course_id=? and "
                        + COURSE_ACCESS_SQL + " and (r.owner_id is null or r.owner_id=? or r.shared=true) order by case when r.kind='QUESTION_BANK' then 0 else 1 end,r.created_at desc",
                (rs, row) -> resourceMap(rs.getLong("id"), rs.getString("kind"), rs.getString("name"), rs.getString("source_label"),
                        rs.getBoolean("shared"), rs.getString("content_type"), (Long) rs.getObject("owner_id"), rs.getTimestamp("created_at").toInstant(), teacherId),
                courseId, teacherId, teacherId, teacherId);
    }

    private Map<String, Object> resource(long id, long teacherId) {
        return jdbc.query("select id,kind,name,source_label,shared,content_type,owner_id,created_at from course_resource where id=?", rs -> {
            if (!rs.next()) throw notFound("资料不存在");
            return resourceMap(rs.getLong("id"), rs.getString("kind"), rs.getString("name"), rs.getString("source_label"),
                    rs.getBoolean("shared"), rs.getString("content_type"), (Long) rs.getObject("owner_id"), rs.getTimestamp("created_at").toInstant(), teacherId);
        }, id);
    }

    private Map<String, Object> resourceMap(long id, String kind, String name, String source, boolean shared, String contentType,
                                             Long ownerId, Instant createdAt, long teacherId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("kind", kind); value.put("name", name); value.put("sourceLabel", source);
        value.put("shared", shared); value.put("contentType", contentType); value.put("ownedByCurrentTeacher", ownerId != null && ownerId == teacherId);
        value.put("createdAt", createdAt); return value;
    }

    private byte[] readStoredResource(String storageKey) {
        try {
            return resourceStorage.read(storageKey);
        } catch (IOException exception) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_ERROR", "璧勬枃璇诲彇澶辫触");
        }
    }

    private void deleteStoredResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            resourceStorage.delete(storageKey);
        } catch (IOException exception) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_STORAGE_ERROR", "璧勬枃鍒犻櫎澶辫触");
        }
    }

    private List<Map<String, Object>> tasks(long courseId, long teacherId) {
        return jdbc.query("select id from learning_task where course_id=? order by created_at desc", (rs, row) -> task(rs.getLong("id"), teacherId), courseId);
    }

    private Map<String, Object> task(long taskId, long teacherId) {
        return jdbc.query("select t.id,t.course_id,t.task_type,t.name,t.description,t.start_at,t.deadline,t.max_score,t.questions_json,t.created_at from learning_task t join course c on c.id=t.course_id where t.id=? and " + COURSE_ACCESS_SQL,
                rs -> {
                    if (!rs.next()) throw notFound("任务不存在或无权访问");
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rs.getLong("id")); value.put("courseId", rs.getLong("course_id"));
                    value.put("taskType", rs.getString("task_type")); value.put("name", rs.getString("name"));
                    value.put("description", rs.getString("description")); value.put("startAt", rs.getTimestamp("start_at").toInstant());
                    value.put("deadline", rs.getTimestamp("deadline").toInstant()); value.put("maxScore", rs.getInt("max_score"));
                    value.put("questions", readJson(rs.getString("questions_json"))); value.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    List<Map<String, Object>> submissions = submissions(taskId);
                    value.put("submissions", submissions);
                    value.put("submittedCount", submissions.stream().filter(row -> Boolean.TRUE.equals(row.get("submitted"))).count());
                    value.put("gradedCount", submissions.stream().filter(row -> row.get("teacherScore") != null).count());
                    value.put("studentCount", submissions.size());
                    return value;
                }, taskId, teacherId, teacherId);
    }

    private List<Map<String, Object>> submissions(long taskId) {
        return jdbc.query("select id,task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,answers_json,report_text,ai_review,teacher_comment,review_status,current_version_no from task_submission where task_id=? order by submitted desc,student_name",
                (rs, row) -> submissionMap(rs.getLong("id"), rs.getLong("task_id"), rs.getString("student_name"), rs.getString("student_no"),
                        rs.getBoolean("submitted"), rs.getTimestamp("submitted_at"), (Integer) rs.getObject("ai_score"),
                        (Integer) rs.getObject("teacher_score"), rs.getString("answers_json"), rs.getString("report_text"),
                        rs.getString("ai_review"), rs.getString("teacher_comment"), rs.getString("review_status"), rs.getInt("current_version_no")), taskId);
    }

    private Map<String, Object> submission(long id) {
        return jdbc.query("select id,task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,answers_json,report_text,ai_review,teacher_comment,review_status,current_version_no from task_submission where id=?", rs -> {
            if (!rs.next()) throw notFound("提交不存在");
            return submissionMap(rs.getLong("id"), rs.getLong("task_id"), rs.getString("student_name"), rs.getString("student_no"),
                    rs.getBoolean("submitted"), rs.getTimestamp("submitted_at"), (Integer) rs.getObject("ai_score"),
                    (Integer) rs.getObject("teacher_score"), rs.getString("answers_json"), rs.getString("report_text"),
                    rs.getString("ai_review"), rs.getString("teacher_comment"), rs.getString("review_status"), rs.getInt("current_version_no"));
        }, id);
    }

    private Map<String, Object> submissionMap(long id, long taskId, String name, String no, boolean submitted, Timestamp submittedAt,
                                               Integer aiScore, Integer teacherScore, String answers, String report, String aiReview, String teacherComment,
                                               String reviewStatus, int currentVersionNo) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("taskId", taskId); value.put("studentName", name); value.put("studentNo", no);
        value.put("submitted", submitted); value.put("submittedAt", submittedAt == null ? null : submittedAt.toInstant());
        value.put("aiScore", aiScore); value.put("teacherScore", teacherScore); value.put("answers", readJson(answers));
        value.put("reportText", report); value.put("aiReview", aiReview); value.put("teacherComment", teacherComment);
        value.put("reviewStatus", reviewStatus); value.put("currentVersionNo", currentVersionNo); return value;
    }

    private Map<String, Object> metrics(long teacherId, List<Map<String, Object>> courses) {
        Integer pending = jdbc.queryForObject("select count(*) from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where " + COURSE_ACCESS_SQL + " and s.submitted=true and s.teacher_score is null", Integer.class, teacherId, teacherId);
        Integer active = jdbc.queryForObject("select count(*) from learning_task t join course c on c.id=t.course_id where " + COURSE_ACCESS_SQL + " and t.start_at<=current_timestamp and t.deadline>=current_timestamp", Integer.class, teacherId, teacherId);
        Double rate = jdbc.queryForObject("select coalesce(100.0*sum(case when s.submitted then 1 else 0 end)/nullif(count(*),0),0) from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where " + COURSE_ACCESS_SQL, Double.class, teacherId, teacherId);
        return Map.of("courseCount", courses.size(), "activeTaskCount", active == null ? 0 : active,
                "pendingReviewCount", pending == null ? 0 : pending, "weeklySubmissionRate", Math.round(rate == null ? 0 : rate));
    }

    private List<Map<String, Object>> alerts(long teacherId) {
        return jdbc.query("select id from teaching_alert where teacher_id=? order by created_at desc", (rs, row) -> alert(rs.getLong("id"), teacherId), teacherId);
    }

    private Map<String, Object> alert(long id, long teacherId) {
        return jdbc.query("select id,title,summary,target_name,level,status,analysis,evidence,proposal,created_at from teaching_alert where id=? and teacher_id=?", rs -> {
            if (!rs.next()) throw notFound("预警不存在");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id")); value.put("title", rs.getString("title")); value.put("summary", rs.getString("summary"));
            value.put("targetName", rs.getString("target_name")); value.put("level", rs.getString("level")); value.put("status", rs.getString("status"));
            value.put("analysis", rs.getString("analysis")); value.put("evidence", rs.getString("evidence")); value.put("proposal", rs.getString("proposal"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant()); return value;
        }, id, teacherId);
    }

    private List<Map<String, Object>> assistantSessions(long teacherId) {
        return jdbc.query("select id from assistant_session where teacher_id=? order by updated_at desc", (rs, row) -> session(rs.getLong("id"), teacherId), teacherId);
    }

    private Map<String, Object> session(long id, long teacherId) {
        return jdbc.query("select id,title,created_at,updated_at from assistant_session where id=? and teacher_id=?", rs -> {
            if (!rs.next()) throw notFound("对话不存在");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id")); value.put("title", rs.getString("title"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant()); value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
            value.put("messages", jdbc.query("select id,role,content,created_at from assistant_message where session_id=? order by created_at",
                    (messageRs, row) -> Map.of("id", messageRs.getLong("id"), "role", messageRs.getString("role"),
                            "content", messageRs.getString("content"), "createdAt", messageRs.getTimestamp("created_at").toInstant()), id));
            return value;
        }, id, teacherId);
    }

    private List<Map<String, Object>> conversations(long teacherId) {
        return jdbc.query("select id from conversation where teacher_id=? order by case when contact_type='SYSTEM' then 0 else 1 end,updated_at desc",
                (rs, row) -> conversation(rs.getLong("id"), teacherId), teacherId);
    }

    private Map<String, Object> conversation(long id, long teacherId) {
        return jdbc.query("select id,student_id,contact_name,contact_type,avatar_text,unread_count,updated_at from conversation where id=? and teacher_id=?", rs -> {
            if (!rs.next()) throw notFound("会话不存在");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id")); value.put("contactName", rs.getString("contact_name"));
            value.put("studentId", (Long) rs.getObject("student_id"));
            value.put("contactType", rs.getString("contact_type")); value.put("avatarText", rs.getString("avatar_text"));
            value.put("unreadCount", rs.getInt("unread_count")); value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
            value.put("messages", jdbc.query("select id,sender,title,content,created_at from conversation_message where conversation_id=? order by created_at",
                    (messageRs, row) -> {
                        Map<String, Object> message = new LinkedHashMap<>();
                        message.put("id", messageRs.getLong("id")); message.put("sender", messageRs.getString("sender"));
                        message.put("title", messageRs.getString("title")); message.put("content", messageRs.getString("content"));
                        message.put("createdAt", messageRs.getTimestamp("created_at").toInstant()); return message;
                    }, id));
            return value;
        }, id, teacherId);
    }

    private void requireCourse(long teacherId, long courseId) {
        Integer count = jdbc.queryForObject("select count(*) from course c where c.id=? and " + COURSE_ACCESS_SQL,
                Integer.class, courseId, teacherId, teacherId);
        if (count == null || count == 0) throw notFound("课程不存在或无权访问");
    }

    private void requireTask(long teacherId, long taskId) {
        Integer count = jdbc.queryForObject("select count(*) from learning_task t join course c on c.id=t.course_id where t.id=? and " + COURSE_ACCESS_SQL,
                Integer.class, taskId, teacherId, teacherId);
        if (count == null || count == 0) throw notFound("任务不存在或无权访问");
    }

    private void requireSession(long teacherId, long sessionId) {
        Integer count = jdbc.queryForObject("select count(*) from assistant_session where id=? and teacher_id=?", Integer.class, sessionId, teacherId);
        if (count == null || count == 0) throw notFound("对话不存在或无权访问");
    }

    private String assistantAnswerWithAi(long teacherId, long sessionId, String prompt) {
        int courseCount = jdbc.queryForObject("select count(*) from course c where " + COURSE_ACCESS_SQL, Integer.class, teacherId, teacherId);
        int pending = jdbc.queryForObject("select count(*) from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where " + COURSE_ACCESS_SQL + " and s.submitted=true and s.teacher_score is null", Integer.class, teacherId, teacherId);
        List<Map<String, String>> messages = jdbc.query("select role,content from assistant_message where session_id=? order by created_at asc", (rs, row) -> Map.of("role", rs.getString("role").equals("ASSISTANT") ? "assistant" : "user", "content", rs.getString("content")), sessionId);
        String system = "你是知序云的教师教学助手。只基于教师授权的课程统计回答，不要编造学生隐私、成绩或不存在的事实。可以帮助分析教学、设计练习和总结风险；涉及学生时给出群体化、可执行且尊重隐私的建议。回答使用简洁中文。当前教师有 " + courseCount + " 门课程，待复核提交 " + pending + " 份。";
        return ai.complete(system, messages);
    }

    private String assistantAnswerLegacy(long teacherId, String prompt) {
        int courseCount = jdbc.queryForObject("select count(*) from course c where " + COURSE_ACCESS_SQL, Integer.class, teacherId, teacherId);
        int pending = jdbc.queryForObject("select count(*) from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where " + COURSE_ACCESS_SQL + " and s.submitted=true and s.teacher_score is null", Integer.class, teacherId, teacherId);
        if (prompt.contains("实验") || prompt.contains("共性")) {
            return "根据当前账号授权的课程统计，实验报告的共性问题集中在实体映射、事务边界和异常处理。建议用一组失败事务案例进行课堂复盘，再布置 3 道针对性练习。目前还有 " + pending + " 份提交待复核。";
        }
        if (prompt.contains("学生") || prompt.contains("关注")) {
            return "建议优先关注连续低于班级均分、按时提交率下降且课程访问减少的学生。当前预警中陈子涵的风险趋势最明显，适合先进行一次简短沟通，再决定是否安排补充练习。";
        }
        if (prompt.contains("题") || prompt.contains("练习")) {
            return "可以按概念辨析、代码判断、故障定位和综合设计四个层次组织练习。建议客观题覆盖 REQUIRED 与 REQUIRES_NEW，简答题要求学生解释异常传播和回滚边界。";
        }
        return "我已在当前教师账号的 " + courseCount + " 门授权课程和本账号历史对话范围内整理信息。建议先明确课程、班级和期望输出形式，我可以继续生成课堂活动、任务题目或教学复盘。";
    }

    private long insert(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("无法读取新增数据编号");
        return keys.getKey().longValue();
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, Object.class); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw badRequest("题目数据格式无效"); }
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key); return value == null ? fallback : String.valueOf(value).trim();
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static Instant instant(Object value, Instant fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try { return Instant.parse(String.valueOf(value)); }
        catch (RuntimeException exception) { throw badRequest("日期时间格式无效"); }
    }

    private static String defaultTaskName(String type) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日HH时mm分ss秒").withZone(java.time.ZoneId.of("Asia/Shanghai"))
                .format(Instant.now()) + ("EXPERIMENT".equals(type) ? "实验" : "作业");
    }

    private static String summarize(String text) { return text.length() > 22 ? text.substring(0, 22) + "..." : text; }
    private static String safeFilename(String name) { return name == null || name.isBlank() ? "未命名文件" : name.replace("\\", "_").replace("/", "_"); }
    private static AuthException badRequest(String message) { return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message); }
    private static AuthException notFound(String message) { return new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }

    public record Download(String filename, String contentType, byte[] content) {}
}
