package cloud.zhixuyun.demo;

import cloud.zhixuyun.auth.AuthService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.auth.UserRepository;
import cloud.zhixuyun.storage.ResourceStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Seeds a disposable local walkthrough only when explicitly enabled. */
@Component
public class DemoDataInitializer {
    private final boolean enabled;
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final UserRepository users;
    private final ObjectMapper json;
    private final ResourceStorage storage;

    public DemoDataInitializer(@Value("${zhixuyun.demo-data:false}") boolean enabled,
                               JdbcTemplate jdbc, AuthService auth, UserRepository users,
                               ObjectMapper json, ResourceStorage storage) {
        this.enabled = enabled;
        this.jdbc = jdbc;
        this.auth = auth;
        this.users = users;
        this.json = json;
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled
                || users.findByLoginName("demo-admin").isPresent()
                || users.findByLoginName("demo-teacher").isPresent()
                || users.findByLoginName("demo-student").isPresent()) return;
        UserAccount admin = user("demo-admin", "演示管理员", Role.ADMIN);
        UserAccount teacher = user("demo-teacher", "林老师", Role.TEACHER);
        UserAccount student = user("demo-student", "陈同学", Role.STUDENT);
        jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                teacher.getId(), "软件工程系", "讲师", "teacher@example.test", "", "负责 Java Web 与工程实践课程");
        long classId = insert("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,true)",
                "2023级软件工程3班", "2023", "软件工程");
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)",
                student.getId(), "20230001", "2023", classId);
        long courseId = insert("insert into course(teacher_id,name,code,class_name,semester,schedule_text,student_count,color) values (?,?,?,?,?,?,?,?)",
                teacher.getId(), "Java Web 应用开发", "SE-JW-2303", "2023级软件工程3班", "2025-2026学年第二学期", "周二 3-4节", 46, "#087f68");
        Instant now = Instant.now();
        long homeworkId = insert("insert into learning_task(course_id,task_type,name,description,start_at,deadline,max_score,questions_json,created_at) values (?,?,?,?,?,?,?,?,?)",
                courseId, "HOMEWORK", "作业 1：Spring 核心概念", "完成 Spring Bean 生命周期与依赖注入练习。", Timestamp.from(now.minusSeconds(86_400)), Timestamp.from(now.plusSeconds(172_800)), 100,
                write(List.of(Map.of("id", 1, "type", "SINGLE", "title", "Spring Bean 默认作用域是什么？", "options", List.of("singleton", "prototype", "request"), "score", 10), Map.of("id", 2, "type", "TRUE_FALSE", "title", "依赖注入可以降低组件之间的耦合。", "options", List.of("正确", "错误"), "score", 10))), Timestamp.from(now.minusSeconds(86_400)));
        long experimentId = insert("insert into learning_task(course_id,task_type,name,description,start_at,deadline,max_score,questions_json,created_at) values (?,?,?,?,?,?,?,?,?)",
                courseId, "EXPERIMENT", "实验 2：数据持久化", "完成一个带事务边界的 CRUD 服务，并提交实验报告。", Timestamp.from(now.minusSeconds(172_800)), Timestamp.from(now.plusSeconds(259_200)), 100, "[]", Timestamp.from(now.minusSeconds(172_800)));
        jdbc.update("insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,report_text,ai_review,teacher_comment,review_status,current_version_no,answers_json) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                experimentId, student.getDisplayName(), "20230001", true, Timestamp.from(now.minusSeconds(21_600)), 86, 92, "本次实验完成了 Repository、Service 与 Controller 的分层实现。", "结构清晰，建议补充事务回滚场景的验证证据。", "报告内容完整，已发布最终成绩。", "PUBLISHED", 1, "{}");
        storeResource(courseId, teacher.getId(), "实验指导.pdf", "application/pdf", "课程资料：数据持久化实验指导");
        storeResource(courseId, teacher.getId(), "题库示例.csv", "text/csv", "课程资料：练习题库");
        jdbc.update("insert into user_notification(user_id,notification_type,title,content,source_type,source_id,is_read,created_at) values (?,?,?,?,?,?,?,?)",
                student.getId(), "TASK", "作业 1 即将截止", "请在截止时间前完成 Spring 核心概念练习。", "TASK", homeworkId, false, Timestamp.from(now));
        jdbc.update("insert into course_enrollment(course_id,student_id,active,joined_at) values (?,?,true,?)", courseId, student.getId(), Timestamp.from(now));
    }

    private UserAccount user(String login, String name, Role role) {
        UserAccount account = new UserAccount(null, login, auth.encodePassword("Demo123!"), name, role, true);
        return users.save(account);
    }

    private void storeResource(long courseId, long teacherId, String name, String contentType, String text) {
        try {
            ResourceStorage.StoredResource stored = storage.store("demo-resources", name, contentType, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jdbc.update("insert into course_resource(course_id,owner_id,kind,name,source_label,shared,content_type,storage_backend,storage_key,file_size,created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                    courseId, teacherId, name.endsWith(".csv") ? "QUESTION_BANK" : "MATERIAL", name, "林老师", true, contentType,
                    stored.storageBackend(), stored.storageKey(), stored.fileSize(), Timestamp.from(Instant.now()));
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建演示课程资料", exception);
        }
    }

    private long insert(String sql, Object... values) {
        org.springframework.jdbc.support.KeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("演示题目初始化失败", exception); }
    }
}
