package cloud.zhixuyun.workflow;

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

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningWorkflowService {
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    public LearningWorkflowService(JdbcTemplate jdbc, AuthSessionService sessions, ObjectMapper json) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.json = json;
    }

    public UserAccount require(String authorization, Role role) {
        UserAccount user = sessions.requireUser(authorization);
        if (user.getRole() != role) throw new AuthException(HttpStatus.FORBIDDEN, role + "_REQUIRED", "当前账号无权访问此功能");
        return user;
    }

    public UserAccount current(String authorization) {
        return sessions.requireUser(authorization);
    }

    @Transactional
    public Map<String, Object> joinCourse(UserAccount student, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.isBlank()) throw badRequest("请输入课程邀请码");
        Map<String, Object> course = jdbc.query("""
                select c.id,c.name,c.code,c.semester,i.expires_at
                from course_invite_code i join course c on c.id=i.course_id
                where i.invite_code=? and i.enabled=true
                """, rs -> {
            if (!rs.next()) throw notFound("邀请码无效或已停用");
            Timestamp expires = rs.getTimestamp("expires_at");
            if (expires != null && expires.toInstant().isBefore(Instant.now())) throw badRequest("邀请码已过期");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("name", rs.getString("name"));
            value.put("code", rs.getString("code"));
            value.put("semester", rs.getString("semester"));
            return value;
        }, normalized);
        long courseId = ((Number) course.get("id")).longValue();
        Integer count = jdbc.queryForObject("select count(*) from course_enrollment where course_id=? and student_id=?", Integer.class, courseId, student.getId());
        if (count != null && count > 0) {
            jdbc.update("update course_enrollment set active=true,joined_at=? where course_id=? and student_id=?", Timestamp.from(Instant.now()), courseId, student.getId());
        } else {
            jdbc.update("insert into course_enrollment(course_id,student_id,active,joined_at) values (?,?,true,?)", courseId, student.getId(), Timestamp.from(Instant.now()));
        }
        notifyUser(student.getId(), "COURSE", "已加入课程", "你已加入“" + course.get("name") + "”。", "COURSE", courseId);
        return course;
    }

    public Map<String, Object> inviteCode(UserAccount teacher, long courseId) {
        requireCourseOwner(teacher.getId(), courseId);
        return jdbc.query("""
                select invite_code,enabled,expires_at,created_at from course_invite_code
                where course_id=? and enabled=true and (expires_at is null or expires_at>current_timestamp)
                order by created_at desc limit 1
                """, rs -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("courseId", courseId);
            if (rs.next()) {
                value.put("code", rs.getString("invite_code"));
                value.put("enabled", rs.getBoolean("enabled"));
                value.put("expiresAt", rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant());
            } else {
                value.put("code", null);
                value.put("enabled", false);
                value.put("expiresAt", null);
            }
            return value;
        }, courseId);
    }

    @Transactional
    public Map<String, Object> regenerateInviteCode(UserAccount teacher, long courseId) {
        requireCourseOwner(teacher.getId(), courseId);
        jdbc.update("update course_invite_code set enabled=false where course_id=?", courseId);
        Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);
        String code;
        do { code = randomCode(); }
        while (Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from course_invite_code where invite_code=?", Boolean.class, code)));
        jdbc.update("insert into course_invite_code(course_id,invite_code,enabled,expires_at,created_at) values (?,?,true,?,?)",
                courseId, code, Timestamp.from(expires), Timestamp.from(Instant.now()));
        return inviteCode(teacher, courseId);
    }

    public List<Map<String, Object>> submissionVersions(UserAccount user, long submissionId) {
        requireSubmissionAccess(user, submissionId);
        List<Map<String, Object>> versions = jdbc.query("""
                select id,version_no,report_text,attachment_json,ai_score,ai_review,created_at
                from submission_version where submission_id=? order by version_no desc
                """, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("versionNo", rs.getInt("version_no"));
            value.put("reportText", rs.getString("report_text"));
            value.put("attachment", readJson(rs.getString("attachment_json")));
            value.put("aiScore", rs.getObject("ai_score"));
            value.put("aiReview", rs.getString("ai_review"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant());
            return value;
        }, submissionId);
        if (!versions.isEmpty()) return versions;
        return jdbc.query("select id,report_text,answers_json,ai_score,ai_review,submitted_at from task_submission where id=?", (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("versionNo", 1);
            value.put("reportText", rs.getString("report_text"));
            value.put("attachment", readJson(rs.getString("answers_json")));
            value.put("aiScore", rs.getObject("ai_score"));
            value.put("aiReview", rs.getString("ai_review"));
            value.put("createdAt", rs.getTimestamp("submitted_at") == null ? Instant.EPOCH : rs.getTimestamp("submitted_at").toInstant());
            return value;
        }, submissionId);
    }

    @Transactional
    public Map<String, Object> returnSubmission(UserAccount teacher, long submissionId, String reason) {
        Map<String, Object> target = submissionTarget(teacher.getId(), submissionId);
        String message = reason == null || reason.isBlank() ? "教师已退回报告，请修改后重新提交。" : reason.trim();
        jdbc.update("update task_submission set review_status='RETURNED',teacher_score=null,teacher_comment=? where id=?", message, submissionId);
        Long studentId = findStudentId(String.valueOf(target.get("studentNo")));
        if (studentId != null) notifyUser(studentId, "REVIEW", "实验报告已退回", message, "SUBMISSION", submissionId);
        return submissionState(submissionId);
    }

    public Map<String, Object> analytics(UserAccount teacher, long taskId) {
        requireTaskOwner(teacher.getId(), taskId);
        Map<String, Object> summary = jdbc.query("""
                select count(*) total_count,
                       sum(case when submitted=true then 1 else 0 end) submitted_count,
                       sum(case when teacher_score is null and submitted=true then 1 else 0 end) pending_count,
                       avg(coalesce(teacher_score,ai_score)) average_score,
                       min(coalesce(teacher_score,ai_score)) minimum_score,
                       max(coalesce(teacher_score,ai_score)) maximum_score
                from task_submission where task_id=?
                """, rs -> {
            rs.next();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("totalCount", rs.getInt("total_count"));
            value.put("submittedCount", rs.getInt("submitted_count"));
            value.put("pendingReviewCount", rs.getInt("pending_count"));
            value.put("averageScore", rs.getObject("average_score"));
            value.put("minimumScore", rs.getObject("minimum_score"));
            value.put("maximumScore", rs.getObject("maximum_score"));
            return value;
        }, taskId);
        List<Map<String, Object>> distribution = jdbc.query("""
                select case when coalesce(teacher_score,ai_score)>=90 then '90-100'
                            when coalesce(teacher_score,ai_score)>=80 then '80-89'
                            when coalesce(teacher_score,ai_score)>=70 then '70-79'
                            when coalesce(teacher_score,ai_score)>=60 then '60-69' else '0-59' end score_range,
                       count(*) score_count
                from task_submission where task_id=? and coalesce(teacher_score,ai_score) is not null
                group by 1 order by 1 desc
                """, (rs, row) -> Map.of("range", rs.getString("score_range"), "count", rs.getInt("score_count")), taskId);
        return Map.of("taskId", taskId, "summary", summary, "distribution", distribution);
    }

    public List<Map<String, Object>> rubrics(UserAccount teacher) {
        return jdbc.query("select id,name,dimensions_json,enabled,version_no,created_at,updated_at from rubric_template where teacher_id=? order by updated_at desc",
                (rs, row) -> rubricRow(rs.getLong("id"), rs.getString("name"), rs.getString("dimensions_json"), rs.getBoolean("enabled"),
                        rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), teacher.getId());
    }

    @Transactional
    public Map<String, Object> createRubric(UserAccount teacher, Map<String, Object> body) {
        String name = requiredText(body, "name", 160);
        Object dimensions = body.get("dimensions");
        if (!(dimensions instanceof List<?> list) || list.isEmpty()) throw badRequest("评价模板至少需要一个维度");
        Instant now = Instant.now();
        jdbc.update("insert into rubric_template(teacher_id,name,dimensions_json,enabled,version_no,created_at,updated_at) values (?,?,?,true,1,?,?)",
                teacher.getId(), name, writeJson(dimensions), Timestamp.from(now), Timestamp.from(now));
        Long id = jdbc.queryForObject("select max(id) from rubric_template where teacher_id=?", Long.class, teacher.getId());
        return rubric(teacher.getId(), id == null ? 0 : id);
    }

    @Transactional
    public Map<String, Object> updateRubric(UserAccount teacher, long id, Map<String, Object> body) {
        String name = requiredText(body, "name", 160);
        Object dimensions = body.get("dimensions");
        if (!(dimensions instanceof List<?> list) || list.isEmpty()) throw badRequest("评价模板至少需要一个维度");
        int changed = jdbc.update("update rubric_template set name=?,dimensions_json=?,version_no=version_no+1,updated_at=? where id=? and teacher_id=?",
                name, writeJson(dimensions), Timestamp.from(Instant.now()), id, teacher.getId());
        if (changed == 0) throw notFound("评价模板不存在");
        return rubric(teacher.getId(), id);
    }

    @Transactional
    public Map<String, Object> setRubricEnabled(UserAccount teacher, long id, boolean enabled) {
        int changed = jdbc.update("update rubric_template set enabled=?,updated_at=? where id=? and teacher_id=?",
                enabled, Timestamp.from(Instant.now()), id, teacher.getId());
        if (changed == 0) throw notFound("评价模板不存在");
        return rubric(teacher.getId(), id);
    }

    public List<Map<String, Object>> notifications(UserAccount user) {
        return jdbc.query("select id,notification_type,title,content,source_type,source_id,is_read,created_at from user_notification where user_id=? order by created_at desc limit 100",
                (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("type", rs.getString("notification_type"));
            value.put("title", rs.getString("title"));
            value.put("content", rs.getString("content"));
            value.put("sourceType", rs.getString("source_type"));
            value.put("sourceId", rs.getObject("source_id"));
            value.put("read", rs.getBoolean("is_read"));
            value.put("createdAt", rs.getTimestamp("created_at").toInstant());
            return value;
        }, user.getId());
    }

    @Transactional
    public void markNotificationRead(UserAccount user, long id) {
        if (jdbc.update("update user_notification set is_read=true where id=? and user_id=?", id, user.getId()) == 0) throw notFound("通知不存在");
    }

    @Transactional
    public int markAllNotificationsRead(UserAccount user) {
        return jdbc.update("update user_notification set is_read=true where user_id=? and is_read=false", user.getId());
    }

    public void notifyUser(long userId, String type, String title, String content, String sourceType, Long sourceId) {
        jdbc.update("insert into user_notification(user_id,notification_type,title,content,source_type,source_id,is_read,created_at) values (?,?,?,?,?,?,false,?)",
                userId, type, title, content, sourceType, sourceId, Timestamp.from(Instant.now()));
    }

    public void notifyCourseStudents(long courseId, String title, String content, String sourceType, Long sourceId) {
        List<Long> ids = jdbc.query("""
                select distinct u.id from user_account u join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                left join course_enrollment ce on ce.student_id=u.id and ce.course_id=? and ce.active=true
                join course c on c.id=?
                where u.role='STUDENT' and (ac.name=c.class_name or ce.id is not null)
                """, (rs, row) -> rs.getLong(1), courseId, courseId);
        ids.forEach(id -> notifyUser(id, "TASK", title, content, sourceType, sourceId));
    }

    public void notifySubmissionStudent(long submissionId, String title, String content) {
        List<String> numbers = jdbc.query("select student_no from task_submission where id=?", (rs, row) -> rs.getString(1), submissionId);
        if (numbers.isEmpty()) return;
        Long studentId = findStudentId(numbers.get(0));
        if (studentId != null) notifyUser(studentId, "REVIEW", title, content, "SUBMISSION", submissionId);
    }

    private Map<String, Object> rubric(long teacherId, long id) {
        return jdbc.query("select id,name,dimensions_json,enabled,version_no,created_at,updated_at from rubric_template where id=? and teacher_id=?", rs -> {
            if (!rs.next()) throw notFound("评价模板不存在");
            return rubricRow(rs.getLong("id"), rs.getString("name"), rs.getString("dimensions_json"), rs.getBoolean("enabled"),
                    rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        }, id, teacherId);
    }

    private Map<String, Object> rubricRow(long id, String name, String dimensions, boolean enabled, int version, Instant createdAt, Instant updatedAt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("name", name); value.put("dimensions", readJson(dimensions)); value.put("enabled", enabled);
        value.put("version", version); value.put("createdAt", createdAt); value.put("updatedAt", updatedAt);
        return value;
    }

    private Map<String, Object> submissionTarget(long teacherId, long submissionId) {
        return jdbc.query("select s.student_no from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where s.id=? and c.teacher_id=?",
                rs -> {
            if (!rs.next()) throw notFound("提交不存在或无权处理");
            return Map.of("studentNo", rs.getString("student_no"));
        }, submissionId, teacherId);
    }

    private Map<String, Object> submissionState(long submissionId) {
        return jdbc.query("select id,review_status,teacher_comment,current_version_no from task_submission where id=?", rs -> {
            if (!rs.next()) throw notFound("提交不存在");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("reviewStatus", rs.getString("review_status"));
            value.put("teacherComment", rs.getString("teacher_comment"));
            value.put("currentVersionNo", rs.getInt("current_version_no"));
            return value;
        }, submissionId);
    }

    private Long findStudentId(String studentNo) {
        List<Long> values = jdbc.query("select user_id from student_profile where student_no=? or replace(student_no,'20230','2023')=?",
                (rs, row) -> rs.getLong(1), studentNo, studentNo);
        return values.isEmpty() ? null : values.get(0);
    }

    private void requireCourseOwner(long teacherId, long courseId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from course where id=? and teacher_id=?", Boolean.class, courseId, teacherId))) throw notFound("课程不存在或无权访问");
    }

    private void requireTaskOwner(long teacherId, long taskId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from learning_task t join course c on c.id=t.course_id where t.id=? and c.teacher_id=?", Boolean.class, taskId, teacherId))) throw notFound("任务不存在或无权访问");
    }

    private void requireSubmissionAccess(UserAccount user, long submissionId) {
        boolean allowed;
        if (user.getRole() == Role.TEACHER) {
            allowed = Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from task_submission s join learning_task t on t.id=s.task_id join course c on c.id=t.course_id where s.id=? and c.teacher_id=?", Boolean.class, submissionId, user.getId()));
        } else if (user.getRole() == Role.STUDENT) {
            allowed = Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from task_submission s join student_profile sp on (sp.student_no=s.student_no or replace(s.student_no,'20230','2023')=sp.student_no) where s.id=? and sp.user_id=?", Boolean.class, submissionId, user.getId()));
        } else allowed = false;
        if (!allowed) throw notFound("提交不存在或无权访问");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        return code.toString();
    }

    private Object readJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return json.readValue(raw, Object.class); }
        catch (JsonProcessingException ignored) { return List.of(); }
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw badRequest("数据格式无效"); }
    }

    private static String requiredText(Map<String, Object> body, String key, int max) {
        String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
        if (value.isBlank() || value.length() > max) throw badRequest("名称不能为空且不能超过 " + max + " 字");
        return value;
    }

    private static AuthException badRequest(String message) { return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message); }
    private static AuthException notFound(String message) { return new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
}
