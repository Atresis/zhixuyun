package cloud.zhixuyun.workflow;

import cloud.zhixuyun.ai.AiGradingService;
import cloud.zhixuyun.auth.AuthService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.auth.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:learning-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:learning-workflow-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class LearningWorkflowControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean AiGradingService grading;

    private String teacherToken;
    private String studentToken;
    private long courseId;
    private long taskId;

    @BeforeEach
    void setUp() throws Exception {
        when(grading.grade(any())).thenReturn(new AiGradingService.GradeResult(81, "AI 初评建议补充结果分析。"));
        users.clear();
        UserAccount teacher = users.save(new UserAccount(null, "workflow-teacher", auth.encodePassword("secret123"), "流程教师", Role.TEACHER, true));
        UserAccount student = users.save(new UserAccount(null, "workflow-student", auth.encodePassword("secret123"), "流程学生", Role.STUDENT, true));
        jdbc.update("insert into teacher_profile(user_id,department,title) values (?,?,?)", teacher.getId(), "软件工程系", "讲师");
        jdbc.update("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,true)", "本班", "2026", "软件工程");
        long classId = jdbc.queryForObject("select max(id) from administrative_class", Long.class);
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)", student.getId(), "20260001", "2026", classId);
        jdbc.update("insert into course(teacher_id,name,code,class_name,semester,schedule_text,student_count,color) values (?,?,?,?,?,?,?,?)",
                teacher.getId(), "跨班实验课程", "WF-001", "其他班", "2026-2027-1", "周三 1-2 节", 0, "#07866f");
        courseId = jdbc.queryForObject("select max(id) from course", Long.class);
        jdbc.update("insert into learning_task(course_id,task_type,name,description,start_at,deadline,max_score,questions_json,created_at) values (?,?,?,?,?,?,?,?,?)",
                courseId, "EXPERIMENT", "实验一", "提交报告", Timestamp.from(Instant.now().minusSeconds(3600)), Timestamp.from(Instant.now().plusSeconds(86400)),
                100, "[]", Timestamp.from(Instant.now().minusSeconds(7200)));
        taskId = jdbc.queryForObject("select max(id) from learning_task", Long.class);
        teacherToken = login("workflow-teacher");
        studentToken = login("workflow-student");
    }

    @Test
    void inviteJoinVersionReturnAndResubmitFlowWorks() throws Exception {
        JsonNode invite = json.readTree(mvc.perform(post("/api/v1/teacher/courses/{courseId}/invite-code", courseId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/student/courses/join")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("code", invite.get("code").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courseId));

        mvc.perform(get("/api/v1/student/workspace").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].name").value("跨班实验课程"));

        submit("第一版报告");
        mvc.perform(post("/api/v1/student/tasks/{id}/text-submission", taskId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("content", "未退回的重复提交"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_ALREADY_EXISTS"));
        long submissionId = jdbc.queryForObject("select max(id) from task_submission where task_id=?", Long.class, taskId);

        mvc.perform(get("/api/v1/submissions/{id}/versions", submissionId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(1));

        jdbc.update("update learning_task set deadline=? where id=?", Timestamp.from(Instant.now().minusSeconds(60)), taskId);

        mvc.perform(post("/api/v1/teacher/submissions/{id}/return", submissionId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"请补充数据证据\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("RETURNED"));

        submit("第二版补充数据证据");
        mvc.perform(get("/api/v1/submissions/{id}/versions", submissionId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[1].versionNo").value(1));

        mvc.perform(post("/api/v1/teacher/submissions/{id}/return", submissionId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"请再次完善结论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("RETURNED"));

        submit("第三版补充数据证据");
        mvc.perform(get("/api/v1/submissions/{id}/versions", submissionId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(3));
    }

    @Test
    void rubricAnalyticsAndNotificationReadAreScoped() throws Exception {
        mvc.perform(post("/api/v1/teacher/rubrics")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"实验报告模板\",\"dimensions\":[{\"name\":\"结果分析\",\"weight\":100}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/teacher/rubrics").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        jdbc.update("insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,review_status,current_version_no) values (?,?,?,?,?,?,?,?)",
                taskId, "流程学生", "20260001", true, Timestamp.from(Instant.now()), 81, "SUBMITTED", 1);
        mvc.perform(get("/api/v1/teacher/tasks/{id}/analytics", taskId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.submittedCount").value(1))
                .andExpect(jsonPath("$.distribution[0].count").value(1));

        long studentId = jdbc.queryForObject("select user_id from student_profile where student_no='20260001'", Long.class);
        jdbc.update("insert into user_notification(user_id,notification_type,title,content,is_read,created_at) values (?,?,?,?,false,?)",
                studentId, "TASK", "测试通知", "通知正文", Timestamp.from(Instant.now()));
        long notificationId = jdbc.queryForObject("select max(id) from user_notification where user_id=?", Long.class, studentId);
        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(true));
    }

    @Test
    void studentCannotReadAnotherStudentsVersions() throws Exception {
        jdbc.update("insert into task_submission(task_id,student_name,student_no,submitted,submitted_at,ai_score,review_status,current_version_no) values (?,?,?,?,?,?,?,?)",
                taskId, "其他学生", "20269999", true, Timestamp.from(Instant.now()), 75, "SUBMITTED", 1);
        long submissionId = jdbc.queryForObject("select max(id) from task_submission where student_no='20269999'", Long.class);
        mvc.perform(get("/api/v1/submissions/{id}/versions", submissionId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isNotFound());
    }

    private void submit(String content) throws Exception {
        mvc.perform(post("/api/v1/student/tasks/{id}/text-submission", taskId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("content", content))))
                .andExpect(status().isOk());
    }

    private String login(String account) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("account", account, "password", "secret123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
