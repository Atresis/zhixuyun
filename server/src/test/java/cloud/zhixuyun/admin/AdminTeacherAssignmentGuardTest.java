package cloud.zhixuyun.admin;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:admin-teacher-guard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:admin-teacher-guard-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class AdminTeacherAssignmentGuardTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    private String adminToken;
    private long studentId;
    private long teacherId;
    private long courseId;
    private long classId;

    @BeforeEach
    void setUp() throws Exception {
        users.clear();
        UserAccount admin = users.save(new UserAccount(null, "admin-guard", auth.encodePassword("secret123"), "测试管理员", Role.ADMIN, true));
        UserAccount teacher = users.save(new UserAccount(null, "teacher-guard", auth.encodePassword("secret123"), "测试教师", Role.TEACHER, true));
        UserAccount student = users.save(new UserAccount(null, "student-guard", auth.encodePassword("secret123"), "测试学生", Role.STUDENT, true));
        teacherId = teacher.getId();
        studentId = student.getId();
        jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                teacherId, "软件工程系", "讲师", "teacher@test.local", "", "");
        jdbc.update("insert into course(teacher_id,name,code,class_name,semester,student_count,color) values (?,?,?,?,?,?,?)",
                teacherId, "测试课程", "COURSE-01", "测试班级", "2026-2027-1", 1, "#087f68");
        courseId = jdbc.queryForObject("select max(id) from course", Long.class);
        jdbc.update("insert into teaching_class(course_id,name,term,enabled) values (?,?,?,true)", courseId, "测试教学班", "2026-2027-1");
        classId = jdbc.queryForObject("select max(id) from teaching_class", Long.class);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin-guard\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login).get("token").asText();
    }

    @Test
    void studentCannotBeAssignedAsCourseTeacher() throws Exception {
        mvc.perform(put("/api/v1/admin/teachers/{teacherId}/courses", studentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignments\":[{\"courseId\":" + courseId + ",\"roleCode\":\"INSTRUCTOR\",\"subjectOrDuty\":\"任课教师\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotBeAssignedAsTeachingClassTeacher() throws Exception {
        mvc.perform(put("/api/v1/admin/classes/{classId}/teacher", classId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + studentId + "}"))
                .andExpect(status().isBadRequest());
    }
}
