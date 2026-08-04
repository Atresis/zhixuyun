package cloud.zhixuyun.student;

import cloud.zhixuyun.ai.AiAssistantClient;
import cloud.zhixuyun.ai.AiGradingService;
import cloud.zhixuyun.auth.AuthException;
import cloud.zhixuyun.auth.AuthSessionService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {
    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final AiAssistantClient ai;
    private final AiGradingService grading;
    private final SubmissionTextExtractor textExtractor;

    public StudentService(JdbcTemplate jdbc, AuthSessionService sessions, ObjectMapper json, AiAssistantClient ai,
                          AiGradingService grading, SubmissionTextExtractor textExtractor) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.json = json;
        this.ai = ai;
        this.grading = grading;
        this.textExtractor = textExtractor;
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
        String className = String.valueOf(profile.get("className"));
        List<Map<String, Object>> courses = courses(className);
        List<Map<String, Object>> tasks = tasks(studentNo, courses);
        List<Map<String, Object>> reports = reports(tasks);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("metrics", metrics(tasks));
        result.put("courses", courses);
        result.put("tasks", tasks);
        result.put("reports", reports);
        result.put("notifications", notifications(tasks));
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

    public Map<String, Object> askAssistant(UserAccount student, String content) {
        return askAssistantWithAi(student, content);
    }

    private Map<String, Object> askAssistantWithAi(UserAccount student, String content) {
        if (content == null || content.isBlank()) throw badRequest("AI 问题不能为空");
        Map<String, Object> profile = profile(student);
        String studentNo = String.valueOf(profile.get("studentNo"));
        List<Map<String, Object>> tasks = tasks(studentNo, courses(String.valueOf(profile.get("className"))));
        String context = writeJson(Map.of("profile", profile, "tasks", tasks));
        String system = "你是知序云的学生学习助手。只基于提供的学生课程和任务数据回答，不要编造成绩、截止时间或政策。给出具体、可执行的学习建议；无法从数据确定时明确说明。回答使用简洁中文。当前学生数据：" + context;
        return Map.of("answer", ai.complete(system, List.of(Map.of("role", "user", "content", content.trim()))));
    }

    private Map<String, Object> askAssistantLegacy(UserAccount student, String content) {
        if (content == null || content.isBlank()) throw badRequest("请输入问题内容");
        Map<String, Object> profile = profile(student);
        String studentNo = String.valueOf(profile.get("studentNo"));
        List<Map<String, Object>> tasks = tasks(studentNo, courses(String.valueOf(profile.get("className"))));
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

    private List<Map<String, Object>> courses(String className) {
        return jdbc.query("""
                select c.id,c.name,c.code,c.class_name,c.semester,c.schedule_text,c.color,u.display_name teacher_name
                from course c
                join user_account u on u.id=c.teacher_id
                where c.class_name=?
                order by c.id
                """, (rs, row) -> {
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
        }, className);
    }

    private List<Map<String, Object>> tasks(String studentNo, List<Map<String, Object>> courses) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> course : courses) {
            long courseId = ((Number) course.get("id")).longValue();
            tasks.addAll(jdbc.query("""
                    select t.id,t.course_id,t.task_type,t.name,t.description,t.start_at,t.deadline,t.max_score,
                           s.id submission_id,s.submitted_at,s.ai_score,s.teacher_score,s.ai_review,s.teacher_comment,s.report_text,s.answers_json
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
        value.put("submissionId", rs.getObject("submission_id"));
        value.put("submittedAt", rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant());
        value.put("aiScore", (Integer) rs.getObject("ai_score"));
        value.put("teacherScore", (Integer) rs.getObject("teacher_score"));
        value.put("aiReview", rs.getString("ai_review"));
        value.put("teacherComment", rs.getString("teacher_comment"));
        value.put("reportText", rs.getString("report_text"));
        value.put("attachment", readJsonObject(rs.getString("answers_json")));
        value.put("submissionStatus", statusOf(rs.getObject("submission_id") != null, rs.getObject("teacher_score"), rs.getString("ai_review")));
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

    private List<Map<String, Object>> notifications(List<Map<String, Object>> tasks) {
        List<Map<String, Object>> notices = new ArrayList<>();
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
        return notices.stream().sorted((left, right) -> compareInstant(right.get("createdAt"), left.get("createdAt"))).limit(10).toList();
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

    private StudentContext context(UserAccount student, long taskId) {
        return jdbc.query("""
                select t.id task_id,t.task_type,t.name task_name,t.description,t.max_score,t.questions_json,
                       t.start_at,t.deadline,sp.student_no,u.display_name
                from learning_task t
                join student_profile sp on sp.user_id=?
                join user_account u on u.id=sp.user_id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                join course c on c.id=t.course_id
                where t.id=? and c.class_name=ac.name
                """, rs -> {
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
        Instant now = Instant.now();
        if (now.isBefore(context.startAt())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TASK_NOT_STARTED", "任务尚未开始，暂不能提交");
        }
        if (now.isAfter(context.deadline())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TASK_CLOSED", "任务已截止，暂不能提交");
        }

        List<Map<String, Object>> reviewed = jdbc.query("""
                select teacher_score,teacher_comment
                from task_submission
                where task_id=? and (student_no=? or replace(student_no,'20230','2023')=?)
                order by id desc
                limit 1
                """, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("teacherScore", rs.getObject("teacher_score"));
            value.put("teacherComment", rs.getString("teacher_comment"));
            return value;
        }, context.taskId(), context.studentNo(), context.studentNo());
        if (!reviewed.isEmpty()) {
            Map<String, Object> current = reviewed.get(0);
            Object teacherScore = current.get("teacherScore");
            String teacherComment = (String) current.get("teacherComment");
            if (teacherScore != null || (teacherComment != null && !teacherComment.isBlank())) {
                throw new AuthException(
                        HttpStatus.CONFLICT,
                        "REPORT_ALREADY_REVIEWED",
                        "教师已完成评分，如需修改请联系教师退回任务");
            }
        }
    }

    private AiGradingService.GradeResult grade(StudentContext context, String submissionText) {
        return grading.grade(new AiGradingService.GradeRequest(
                context.taskType(), context.taskName(), context.description(), context.maxScore(),
                context.questionsJson(), submissionText));
    }

    private void upsertSubmission(StudentContext context, String reportText, String attachmentJson,
                                  AiGradingService.GradeResult grade) {
        Integer count = jdbc.queryForObject("""
                select count(*) from task_submission
                where task_id=? and (student_no=? or replace(student_no,'20230','2023')=?)
                """, Integer.class, context.taskId(), context.studentNo(), context.studentNo());
        if (count != null && count > 0) {
            jdbc.update("""
                    update task_submission
                    set student_name=?,student_no=?,submitted=true,submitted_at=?,ai_score=?,teacher_score=null,answers_json=?,report_text=?,ai_review=?,teacher_comment=null
                    where task_id=? and (student_no=? or replace(student_no,'20230','2023')=?)
                    """, context.displayName(), context.studentNo(), Timestamp.from(Instant.now()), grade.score(), attachmentJson == null ? "[]" : attachmentJson,
                    reportText, grade.review(), context.taskId(), context.studentNo(), context.studentNo());
            return;
        }
        jdbc.update("""
                insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,answers_json,report_text,ai_review,teacher_comment)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, context.taskId(), context.displayName(), context.studentNo(), true, Timestamp.from(Instant.now()), grade.score(), null,
                attachmentJson == null ? "[]" : attachmentJson, reportText, grade.review(), null);
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

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw badRequest("提交内容序列化失败");
        }
    }

    private static String statusOf(boolean submitted, Object teacherScore, String aiReview) {
        if (!submitted) return "待提交";
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
}
