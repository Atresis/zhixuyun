package cloud.zhixuyun.teacher;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:teacher-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:teacher-controller-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "zhixuyun.resource-storage.base-dir=target/test-resource-storage/teacher-controller"
})
@AutoConfigureMockMvc
class TeacherControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    private String token;
    private long teacherId;
    private long alertId;
    private long courseId;
    private long studentId;
    private long outsideStudentId;
    private final Path storageDir = Path.of("target", "test-resource-storage", "teacher-controller");

    @BeforeEach
    void setUp() throws Exception {
        deleteRecursively(storageDir);
        users.clear();
        UserAccount teacher = users.save(new UserAccount(null, "teacher-test", auth.encodePassword("secret123"), "测试教师", Role.TEACHER, true));
        teacherId = teacher.getId();
        jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                teacher.getId(), "软件工程系", "讲师", "teacher@test.local", "", "");
        jdbc.update("insert into course(teacher_id,name,code,class_name,semester,student_count,color) values (?,?,?,?,?,?,?)",
                teacher.getId(), "测试课程", "TEST-01", "测试班级", "2025-2026学年第二学期", 1, "#087f68");
        courseId = jdbc.queryForObject("select max(id) from course where teacher_id=?", Long.class, teacher.getId());
        String className = jdbc.queryForObject("select class_name from course where id=?", String.class, courseId);
        jdbc.update("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,true)", className, "2025", "Software Engineering");
        long classId = jdbc.queryForObject("select max(id) from administrative_class where name=?", Long.class, className);
        jdbc.update("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,true)", "outside-class", "2025", "Software Engineering");
        long outsideClassId = jdbc.queryForObject("select max(id) from administrative_class where name=?", Long.class, "outside-class");
        UserAccount student = users.save(new UserAccount(null, "student-in-class", auth.encodePassword("secret123"), "Student A", Role.STUDENT, true));
        UserAccount outsideStudent = users.save(new UserAccount(null, "student-outside", auth.encodePassword("secret123"), "Student B", Role.STUDENT, true));
        studentId = student.getId(); outsideStudentId = outsideStudent.getId();
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)", studentId, "S001", "2025", classId);
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)", outsideStudentId, "S002", "2025", outsideClassId);
        jdbc.update("insert into teaching_alert(teacher_id,title,summary,target_name,level,status,analysis,evidence,created_at) values (?,?,?,?,?,?,?,?,?)",
                teacher.getId(), "测试预警", "测试摘要", "测试班级", "HIGH", "UNREAD", "分析", "依据", Timestamp.from(Instant.now()));
        alertId = jdbc.queryForObject("select max(id) from teaching_alert where teacher_id=?", Long.class, teacher.getId());
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"teacher-test\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    }

    @Test
    void workspaceUsesAuthenticatedTeacherBoundary() throws Exception {
        mvc.perform(get("/api/v1/teacher/workspace").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("测试教师"))
                .andExpect(jsonPath("$.alerts[0].title").value("测试预警"));
    }

    @Test
    void assignedInstructorCanSeeAndManageCourse() throws Exception {
        UserAccount lead = users.save(new UserAccount(null, "lead-teacher", auth.encodePassword("secret123"), "主讲教师", Role.TEACHER, true));
        jdbc.update("update course set teacher_id=? where id=?", lead.getId(), courseId);
        jdbc.update("insert into course_teacher_assignment(course_id,teacher_id,role_code,subject_or_duty) values (?,?,?,?)",
                courseId, teacherId, "INSTRUCTOR", "任课教师");

        mvc.perform(get("/api/v1/teacher/workspace").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].id").value(courseId))
                .andExpect(jsonPath("$.courses[0].name").value("测试课程"));

        mvc.perform(post("/api/v1/teacher/courses/{id}/tasks", courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"HOMEWORK\",\"name\":\"协同课程作业\",\"startAt\":\"2099-01-01T00:00:00Z\",\"deadline\":\"2099-12-31T23:59:59Z\",\"maxScore\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("协同课程作业"));
    }

    @Test
    void openingAlertMarksItRead() throws Exception {
        mvc.perform(patch("/api/v1/teacher/alerts/{id}/read", alertId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    void taskQuestionsAndGradeFlowPersistToH2() throws Exception {
        String created = mvc.perform(post("/api/v1/teacher/courses/{id}/tasks", courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"HOMEWORK\",\"name\":\"测试作业\",\"startAt\":\"2099-01-01T00:00:00Z\",\"deadline\":\"2099-12-31T23:59:59Z\",\"maxScore\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("测试作业"))
                .andReturn().getResponse().getContentAsString();
        long taskId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();

        mvc.perform(put("/api/v1/teacher/tasks/{id}/questions", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":1,\"type\":\"SINGLE\",\"title\":\"测试题\",\"options\":[\"A\",\"B\"],\"answer\":\"A\",\"score\":10}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].title").value("测试题"));

        jdbc.update("insert into task_submission(task_id,student_name,student_no,submitted,ai_score) values (?,?,?,?,?)",
                taskId, "测试学生", "S001", true, 80);
        long submissionId = jdbc.queryForObject("select max(id) from task_submission where task_id=?", Long.class, taskId);
        mvc.perform(put("/api/v1/teacher/submissions/{id}/grade", submissionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherScore\":88,\"teacherComment\":\"已复核\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherScore").value(88));
        mvc.perform(put("/api/v1/teacher/submissions/{id}/grade", submissionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherScore\":101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contactCandidatesAreLimitedToTeachersCourseClass() throws Exception {
        mvc.perform(get("/api/v1/teacher/contact-candidates")
                        .header("Authorization", "Bearer " + token)
                        .param("courseId", String.valueOf(courseId)).param("q", "S00").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(studentId))
                .andExpect(jsonPath("$.items[0].studentNo").value("S001"));
    }

    @Test
    void firstMessageCreatesConversationAndOutsideStudentIsRejected() throws Exception {
        assertEquals(0, jdbc.queryForObject("select count(*) from conversation", Integer.class));
        mvc.perform(post("/api/v1/teacher/conversations")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + outsideStudentId + ",\"content\":\"hello\"}"))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from conversation", Integer.class));

        mvc.perform(post("/api/v1/teacher/conversations")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"content\":\"first message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.messages[0].content").value("first message"));
        assertEquals(1, jdbc.queryForObject("select count(*) from conversation", Integer.class));
    }

    @Test
    void uploadedResourceIsStoredOnDiskAndDeletedWithRecord() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lesson-notes.txt", "text/plain", "resource-body".getBytes());

        String created = mvc.perform(multipart("/api/v1/teacher/courses/{id}/resources", courseId)
                        .file(file)
                        .param("kind", "MATERIAL")
                        .param("shared", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("lesson-notes.txt"))
                .andReturn().getResponse().getContentAsString();

        long resourceId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();
        String storageKey = jdbc.queryForObject("select storage_key from course_resource where id=?", String.class, resourceId);
        assertTrue(storageKey != null && !storageKey.isBlank());
        assertEquals(null, jdbc.queryForObject("select content from course_resource where id=?", byte[].class, resourceId));
        Path storedFile = storageDir.resolve(storageKey).normalize();
        assertTrue(Files.exists(storedFile));

        mvc.perform(get("/api/v1/teacher/resources/{id}/download", resourceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/teacher/resources/{id}", resourceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertFalse(Files.exists(storedFile));
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }
}
