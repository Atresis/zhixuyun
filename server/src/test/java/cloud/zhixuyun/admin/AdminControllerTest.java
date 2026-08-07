package cloud.zhixuyun.admin;

import cloud.zhixuyun.auth.AuthService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.auth.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:admin-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:admin-controller-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class AdminControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private long teacherId;

    @BeforeEach
    void setUp() throws Exception {
        users.clear();
        jdbc.update("delete from teacher_profile");
        jdbc.update("delete from student_profile");
        jdbc.update("delete from course_teacher_assignment");
        jdbc.update("delete from teaching_class_teacher_assignment");
        jdbc.update("delete from teaching_class");
        jdbc.update("delete from course");
        jdbc.update("delete from administrative_class");

        UserAccount admin = users.save(new UserAccount(null, "admin-test", auth.encodePassword("secret123"), "测试管理员", Role.ADMIN, true));
        UserAccount teacher = users.save(new UserAccount(null, "teacher-test", auth.encodePassword("secret123"), "测试教师", Role.TEACHER, true));
        teacherId = teacher.getId();
        jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                teacherId, "软件工程系", "讲师", "teacher@test.com", "13800000000", "bio");
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin-test\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
    }

    @Test
    void adminCanReadDashboardAndCreateStudent() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.userCount").value(2));

        insertCodedAdministrativeClass("2026级软件工程1班", "2026", "03", "01", "01");

        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"2603010101","displayName":"测试学生","role":"STUDENT","studentNo":"2603010101","gradeYear":"2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));

        Integer count = jdbc.queryForObject("select count(*) from student_profile where student_no='2603010101'", Integer.class);
        Assertions.assertEquals(1, count);
    }

    @Test
    void businessJdbcTemplateUsesPrimaryDataSource() throws Exception {
        String url = jdbc.getDataSource().getConnection().getMetaData().getURL();
        Assertions.assertTrue(url.contains("admin-controller"));
        Assertions.assertFalse(url.contains("admin-controller-backup"));
    }

    @Test
    void courseArrangementIsVisibleToAssignedTeacherAndAdministrativeClassStudent() throws Exception {
        UserAccount assignedTeacher = users.save(new UserAccount(null, "assigned-teacher", auth.encodePassword("secret123"), "安排教师", Role.TEACHER, true));
        jdbc.update("insert into teacher_profile(user_id,department,title,email,phone,bio) values (?,?,?,?,?,?)",
                assignedTeacher.getId(), "软件工程系", "讲师", "assigned@test.com", "", "");
        long administrativeClassId = insertAdministrativeClass("2026级软件工程联动班", "2026");
        long studentId = createStudent("arranged-student", "20260008", administrativeClassId, "2026");
        long courseId = createCourse("课程安排联动测试", "LINK001");

        String createdClass = mvc.perform(post("/api/v1/admin/courses/{courseId}/classes", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"administrativeClassId":%d,"name":"2026级软件工程联动班","term":"2026-2027-1","enabled":true}
                                """.formatted(administrativeClassId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.administrativeClassId").value(administrativeClassId))
                .andReturn().getResponse().getContentAsString();
        long teachingClassId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(createdClass).get("id").asLong();

        mvc.perform(put("/api/v1/admin/classes/{classId}/teacher", teachingClassId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + assignedTeacher.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(assignedTeacher.getId()));

        String teacherToken = login("assigned-teacher");
        mvc.perform(get("/api/v1/teacher/workspace").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].id").value(courseId));

        String studentToken = login("arranged-student");
        mvc.perform(get("/api/v1/student/workspace").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].id").value(courseId))
                .andExpect(jsonPath("$.courses[0].teacherName").value("安排教师"));

        Assertions.assertEquals(studentId, jdbc.queryForObject(
                "select user_id from student_profile where administrative_class_id=?", Long.class, administrativeClassId));
    }

    @Test
    void adminCanSearchAuditLogs() throws Exception {
        jdbc.update("insert into audit_log(actor_id,action,target_type,target_id,detail,created_at) values (?,?,?,?,?,current_timestamp)",
                teacherId, "UPDATE_COURSE", "COURSE", "42", "日志检索唯一标记");
        mvc.perform(get("/api/v1/admin/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "日志检索唯一标记"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("UPDATE_COURSE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void batchImportStudentsCreatesProfiles() throws Exception {
        long classId = insertCodedAdministrativeClass("2026级软件工程1班", "2026", "03", "01", "01");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.csv",
                "text/csv",
                ("studentNo,displayName\n" +
                        "2603010101,学生甲\n" +
                        "2603010102,学生乙\n").getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/api/v1/admin/users/import")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0));

        Integer count = jdbc.queryForObject("select count(*) from student_profile where administrative_class_id=?", Integer.class, classId);
        Assertions.assertEquals(2, count);
    }

    @Test
    void studentNumberSetsAccountPasswordClassAndFirstLoginPolicy() throws Exception {
        long classId = insertCodedAdministrativeClass("2023级软件工程5班", "2023", "03", "01", "05");

        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"2303010507","studentNo":"2303010507","displayName":"十位学号学生","role":"STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.administrativeClassId").value(classId))
                .andExpect(jsonPath("$.gradeYear").value("2023"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"2303010507\",\"password\":\"010507\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }

    @Test
    void studentLoginNameMustEqualStudentNumber() throws Exception {
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"student-alias","studentNo":"2303010507","displayName":"错误账号","role":"STUDENT"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recognizedStudentNumberRequiresOneMatchingClass() throws Exception {
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"2699990101","studentNo":"2699990101","displayName":"未配置班级学生","role":"STUDENT"}
                                """))
                .andExpect(status().isBadRequest());
        Assertions.assertTrue(users.findByLoginName("2699990101").isEmpty());
    }

    @Test
    void studentNumberMustContainExactlyTenDigits() throws Exception {
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"202603010101","studentNo":"202603010101","displayName":"无效学号学生","role":"STUDENT"}
                                """))
                .andExpect(status().isBadRequest());
        Assertions.assertTrue(users.findByLoginName("202603010101").isEmpty());
    }

    @Test
    void transferStudentUpdatesAdministrativeClass() throws Exception {
        long fromClassId = insertAdministrativeClass("2026级软件工程1班", "2026");
        long toClassId = insertAdministrativeClass("2026级软件工程2班", "2027");
        long studentId = createStudent("student-transfer", "20260004", fromClassId, "2026");

        mvc.perform(put("/api/v1/admin/users/{id}/transfer", studentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"administrativeClassId\":" + toClassId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.administrativeClassName").value("2026级软件工程2班"))
                .andExpect(jsonPath("$.gradeYear").value("2027"));

        String gradeYear = jdbc.queryForObject("select grade_year from student_profile where user_id=?", String.class, studentId);
        Assertions.assertEquals("2027", gradeYear);
    }

    @Test
    void adminCanUpdateAndDeleteCourse() throws Exception {
        long courseId = createCourse("数据库原理", "DB001");

        mvc.perform(put("/api/v1/admin/courses/{id}", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"数据库系统","code":"DB002","semester":"2026-2027-2","teacherId":%d}
                                """.formatted(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("数据库系统"))
                .andExpect(jsonPath("$.code").value("DB002"));

        mvc.perform(delete("/api/v1/admin/courses/{id}", courseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject("select count(*) from course where id=?", Integer.class, courseId);
        Assertions.assertEquals(0, count);
    }

    @Test
    void adminCanUpdateAndDeleteTeachingClass() throws Exception {
        long courseId = createCourse("操作系统", "OS001");
        long classId = insertTeachingClass(courseId, "软件工程1班", "2026-2027-1");

        mvc.perform(put("/api/v1/admin/teaching-classes/{id}", classId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"软件工程实验班","term":"2026-2027-2","enabled":false,"teacherId":%d}
                                """.formatted(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("软件工程实验班"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.teacherId").value(teacherId));

        mvc.perform(delete("/api/v1/admin/teaching-classes/{id}", classId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject("select count(*) from teaching_class where id=?", Integer.class, classId);
        Assertions.assertEquals(0, count);
    }

    @Test
    void adminCanUpdateAndDeleteAdministrativeClass() throws Exception {
        long classId = insertAdministrativeClass("2026级人工智能1班", "2026");

        mvc.perform(put("/api/v1/admin/administrative-classes/{id}", classId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"2026级人工智能实验班","gradeYear":"2026","majorName":"人工智能","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026级人工智能实验班"))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(delete("/api/v1/admin/administrative-classes/{id}", classId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject("select count(*) from administrative_class where id=?", Integer.class, classId);
        Assertions.assertEquals(0, count);
    }

    @Test
    void deleteAdministrativeClassFailsWhenStudentsStillLinked() throws Exception {
        long classId = insertAdministrativeClass("2026级网络工程1班", "2026");
        createStudent("student-locked", "20260005", classId, "2026");

        mvc.perform(delete("/api/v1/admin/administrative-classes/{id}", classId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningStudentAsTeacherIsRejected() throws Exception {
        long studentId = createStudent("student-not-teacher", "20260006", null, "2026");
        long courseId = createCourse("软件测试", "TEST001");

        mvc.perform(put("/api/v1/admin/courses/{id}", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId":%d}
                                """.formatted(studentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotReadAdminApi() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"teacher-test\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private long createStudent(String loginName, String studentNo, Long administrativeClassId, String gradeYear) {
        UserAccount student = users.save(new UserAccount(null, loginName, auth.encodePassword("secret123"), loginName, Role.STUDENT, true));
        jdbc.update("insert into student_profile(user_id,student_no,grade_year,administrative_class_id) values (?,?,?,?)",
                student.getId(), studentNo, gradeYear, administrativeClassId);
        return student.getId();
    }

    private String login(String account) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    }

    private long insertAdministrativeClass(String name, String gradeYear) {
        jdbc.update("insert into administrative_class(name,grade_year,major_name,enabled) values (?,?,?,?)",
                name, gradeYear, "软件工程", true);
        return jdbc.queryForObject("select max(id) from administrative_class", Long.class);
    }

    private long insertCodedAdministrativeClass(String name, String gradeYear, String collegeCode, String majorCode, String classCode) {
        jdbc.update("""
                        insert into administrative_class(name,grade_year,college_name,college_code,major_name,major_code,class_code,enabled)
                        values (?,?,?,?,?,?,?,?)
                        """,
                name, gradeYear, "软件学院", collegeCode, "软件工程", majorCode, classCode, true);
        return jdbc.queryForObject("select max(id) from administrative_class", Long.class);
    }

    private long createCourse(String name, String code) {
        jdbc.update("insert into course(teacher_id,name,code,class_name,semester,schedule_text,student_count,color) values (?,?,?,?,?,?,?,?)",
                teacherId, name, code, "默认班级", "2026-2027-1", "周三 1-2 节", 0, "#07876e");
        long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
        jdbc.update("insert into course_teacher_assignment(course_id,teacher_id,role_code,subject_or_duty) values (?,?,?,?)",
                courseId, teacherId, "LEAD", "负责人");
        return courseId;
    }

    private long insertTeachingClass(long courseId, String name, String term) {
        jdbc.update("insert into teaching_class(course_id,name,term,enabled) values (?,?,?,?)", courseId, name, term, true);
        return jdbc.queryForObject("select max(id) from teaching_class", Long.class);
    }
}
