package cloud.zhixuyun.student;

import cloud.zhixuyun.ai.AiGradingService;
import cloud.zhixuyun.auth.AuthException;
import cloud.zhixuyun.auth.AuthService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:student-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:student-controller-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class StudentControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean AiGradingService grading;
    private String token;
    private long taskId;

    @BeforeEach
    void setUp() throws Exception {
        when(grading.grade(any())).thenReturn(new AiGradingService.GradeResult(73, "结构清楚，但结果分析需要补充数据证据。"));
        users.clear();
        UserAccount teacher = users.save(new UserAccount(null, "teacher", auth.encodePassword("secret123"), "测试教师", Role.TEACHER, true));
        UserAccount student = users.save(new UserAccount(null, "student", auth.encodePassword("secret123"), "测试学生", Role.STUDENT, true));
        jdbc.update("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,true)", "2023级软件工程3班", "2023", "软件工程");
        long classId = jdbc.queryForObject("select max(id) from administrative_class", Long.class);
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)",
                student.getId(), "20230001", "2023", classId);
        jdbc.update("insert into course(teacher_id,name,code,class_name,semester,schedule_text,student_count,color) values (?,?,?,?,?,?,?,?)",
                teacher.getId(), "Java Web 应用开发", "SE-JW-2303", "2023级软件工程3班", "2025-2026学年第二学期", "周二 3-4节", 46, "#087f68");
        long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
        jdbc.update("insert into learning_task(course_id,task_type,name,description,start_at,deadline,max_score,questions_json,created_at) values (?,?,?,?,?,?,?,?,?)",
                courseId, "EXPERIMENT", "实验 6：Spring Boot 数据持久化", "提交实验报告", Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now().plusSeconds(86400)), 100, "[]", Timestamp.from(Instant.now().minusSeconds(7200)));
        taskId = jdbc.queryForObject("select max(id) from learning_task", Long.class);
        jdbc.update("insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,teacher_score,report_text,ai_review) values (?,?,?,?,?,?,?,?,?)",
                taskId, "测试学生", "202300001", true, Timestamp.from(Instant.now().minusSeconds(1800)), 84, null, "旧报告", "已有 AI 初评");
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"student\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    }

    @Test
    void workspaceReturnsStudentScopedTasks() throws Exception {
        mvc.perform(get("/api/v1/student/workspace").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.studentNo").value("20230001"))
                .andExpect(jsonPath("$.tasks[0].name").value("实验 6：Spring Boot 数据持久化"))
                .andExpect(jsonPath("$.tasks[0].submissionStatus").value("AI 初评完成"));
    }

    @Test
    void studentCanSubmitTextAndAskAssistant() throws Exception {
        mvc.perform(post("/api/v1/student/tasks/{taskId}/text-submission", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这是新的实验报告文本\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].reportText").value("这是新的实验报告文本"))
                .andExpect(jsonPath("$.reports[0].aiScore").value(73))
                .andExpect(jsonPath("$.reports[0].aiReview").value("结构清楚，但结果分析需要补充数据证据。"));

        mvc.perform(post("/api/v1/student/assistant/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"帮我总结待完成实验任务\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    void studentCanSubmitFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.txt",
                MediaType.TEXT_PLAIN_VALUE, "实验结果表明连接池配置生效".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/student/tasks/{taskId}/file-submission", taskId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].attachment.fileName").value("report.txt"))
                .andExpect(jsonPath("$.reports[0].reportText").value("实验结果表明连接池配置生效"))
                .andExpect(jsonPath("$.reports[0].aiScore").value(73));
    }

    @Test
    void aiFailureDoesNotOverwriteExistingSubmission() throws Exception {
        when(grading.grade(any())).thenThrow(new AuthException(
                HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "模型服务请求失败"));

        mvc.perform(post("/api/v1/student/tasks/{taskId}/text-submission", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这次提交不应覆盖旧报告\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));

        assertEquals(84, jdbc.queryForObject("select ai_score from task_submission where task_id=?", Integer.class, taskId));
        assertEquals("旧报告", jdbc.queryForObject("select report_text from task_submission where task_id=?", String.class, taskId));
        assertEquals("已有 AI 初评", jdbc.queryForObject("select ai_review from task_submission where task_id=?", String.class, taskId));
    }

    @Test
    void reviewedSubmissionCannotBeOverwritten() throws Exception {
        jdbc.update("update task_submission set teacher_score=?,teacher_comment=? where task_id=?",
                92, "教师最终评语", taskId);

        mvc.perform(post("/api/v1/student/tasks/{taskId}/text-submission", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"试图覆盖评分的新报告\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_REVIEWED"));

        assertEquals(92, jdbc.queryForObject("select teacher_score from task_submission where task_id=?", Integer.class, taskId));
        assertEquals("教师最终评语", jdbc.queryForObject("select teacher_comment from task_submission where task_id=?", String.class, taskId));
    }

    @Test
    void submissionBeforeTaskStartIsRejected() throws Exception {
        jdbc.update("update learning_task set start_at=?,deadline=? where id=?",
                Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(Instant.now().plusSeconds(7200)),
                taskId);

        mvc.perform(post("/api/v1/student/tasks/{taskId}/text-submission", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"过早提交\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_NOT_STARTED"));
    }

    @Test
    void submissionAfterDeadlineIsRejected() throws Exception {
        jdbc.update("update learning_task set start_at=?,deadline=? where id=?",
                Timestamp.from(Instant.now().minusSeconds(7200)),
                Timestamp.from(Instant.now().minusSeconds(3600)),
                taskId);

        mvc.perform(post("/api/v1/student/tasks/{taskId}/text-submission", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"逾期提交\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_CLOSED"));
    }
}
