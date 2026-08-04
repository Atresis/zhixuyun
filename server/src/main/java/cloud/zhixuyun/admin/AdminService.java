package cloud.zhixuyun.admin;

import cloud.zhixuyun.auth.AuthException;
import cloud.zhixuyun.auth.AuthService;
import cloud.zhixuyun.auth.AuthSessionService;
import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import cloud.zhixuyun.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminService {
    private static final Duration STUDENT_ARCHIVE_RETENTION = Duration.ofHours(72);

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuthService auth;
    private final AuthSessionService sessions;
    private final BackupArchiveRepository backups;
    private final StudentNumberService studentNumbers;
    private final StudentImportParser studentImportParser;

    public AdminService(JdbcTemplate jdbc, UserRepository users, AuthService auth, AuthSessionService sessions,
                        BackupArchiveRepository backups, StudentNumberService studentNumbers, StudentImportParser studentImportParser) {
        this.jdbc = jdbc;
        this.users = users;
        this.auth = auth;
        this.sessions = sessions;
        this.backups = backups;
        this.studentNumbers = studentNumbers;
        this.studentImportParser = studentImportParser;
    }

    public UserAccount requireAdmin(String authorization) {
        UserAccount user = sessions.requireUser(authorization);
        if (user.getRole() != Role.ADMIN) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin account required");
        }
        return user;
    }

    public Map<String, Object> dashboard(UserAccount actor) {
        int purgedCount = backups.purgeExpired(Instant.now());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", Map.of(
                "userCount", count("select count(*) from user_account"),
                "studentCount", count("select count(*) from user_account where role='STUDENT'"),
                "teacherCount", count("select count(*) from user_account where role='TEACHER'"),
                "courseCount", count("select count(*) from course"),
                "classCount", count("select count(*) from teaching_class"),
                "logCount", count("select count(*) from audit_log")
        ));
        result.put("users", users(0, 6, "", ""));
        result.put("health", Map.of(
                "api", "OK",
                "database", databaseName(),
                "backupDatabase", "Connected",
                "ai", "Demo"
        ));
        result.put("archive", Map.of(
                "latestBackups", backups.latestBackups().stream().limit(5).toList(),
                "purgedCount", purgedCount
        ));
        return result;
    }

    public List<Map<String, Object>> users(int page, int size, String keyword, String role) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<Map<String, Object>> all = jdbc.query("""
                select u.id,u.login_name,u.display_name,u.role,u.enabled,u.email,
                       sp.student_no,sp.grade_year,sp.administrative_class_id,ac.name administrative_class_name
                from user_account u
                left join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                where (?='' or lower(u.login_name) like lower(?) or lower(u.display_name) like lower(?) or lower(coalesce(u.email,'')) like lower(?) or lower(coalesce(sp.student_no,'')) like lower(?))
                  and (?='' or u.role=?)
                order by u.id desc
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("loginName", rs.getString("login_name"));
            item.put("displayName", rs.getString("display_name"));
            item.put("role", rs.getString("role"));
            item.put("enabled", rs.getBoolean("enabled"));
            item.put("email", rs.getString("email"));
            item.put("studentNo", rs.getString("student_no"));
            item.put("gradeYear", rs.getString("grade_year"));
            item.put("administrativeClassId", rs.getObject("administrative_class_id"));
            item.put("administrativeClassName", rs.getString("administrative_class_name"));
            return item;
        }, normalizedKeyword, like(normalizedKeyword), like(normalizedKeyword), like(normalizedKeyword), like(normalizedKeyword), normalizedRole, normalizedRole);
        int normalizedSize = Math.max(1, Math.min(size, 200));
        int normalizedPage = Math.max(0, page);
        int from = Math.min(normalizedPage * normalizedSize, all.size());
        int to = Math.min(from + normalizedSize, all.size());
        return all.subList(from, to);
    }

    public Map<String, Object> userSearch(int page, int size, String keyword, String role) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<Map<String, Object>> all = jdbc.query("""
                select u.id,u.login_name,u.display_name,u.role,u.enabled,u.email,
                       sp.student_no,sp.grade_year,sp.administrative_class_id,ac.name administrative_class_name
                from user_account u
                left join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                where (?='' or lower(u.login_name) like lower(?) or lower(u.display_name) like lower(?) or lower(coalesce(u.email,'')) like lower(?) or lower(coalesce(sp.student_no,'')) like lower(?))
                  and (?='' or u.role=?)
                order by u.id desc
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("loginName", rs.getString("login_name"));
            item.put("displayName", rs.getString("display_name"));
            item.put("role", rs.getString("role"));
            item.put("enabled", rs.getBoolean("enabled"));
            item.put("email", rs.getString("email"));
            item.put("studentNo", rs.getString("student_no"));
            item.put("gradeYear", rs.getString("grade_year"));
            item.put("administrativeClassId", rs.getObject("administrative_class_id"));
            item.put("administrativeClassName", rs.getString("administrative_class_name"));
            return item;
        }, normalizedKeyword, like(normalizedKeyword), like(normalizedKeyword), like(normalizedKeyword), like(normalizedKeyword), normalizedRole, normalizedRole);

        int normalizedSize = Math.max(1, Math.min(size, 200));
        int normalizedPage = Math.max(0, page);
        int from = Math.min(normalizedPage * normalizedSize, all.size());
        int to = Math.min(from + normalizedSize, all.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", all.subList(from, to));
        result.put("page", normalizedPage);
        result.put("size", normalizedSize);
        result.put("totalElements", all.size());
        result.put("totalPages", all.isEmpty() ? 0 : (int) Math.ceil(all.size() / (double) normalizedSize));
        return result;
    }

    public List<Map<String, Object>> teachers(int page, int size, String keyword) {
        return users(page, size, keyword, "TEACHER");
    }

    public Map<String, Object> teacherDetail(long teacherId) {
        UserAccount teacher = requireTeacherAccount(teacherId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", teacher.getId());
        result.put("loginName", teacher.getLoginName());
        result.put("displayName", teacher.getDisplayName());
        result.put("email", teacher.getEmail());
        result.put("phone", teacher.getPhone());
        result.put("bio", teacher.getBio());
        Map<String, Object> profile = jdbc.query("""
                select department,title,email,phone,bio
                from teacher_profile
                where user_id=?
                """, rs -> {
            if (!rs.next()) return Map.<String, Object>of();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("department", rs.getString("department"));
            row.put("title", rs.getString("title"));
            row.put("email", rs.getString("email"));
            row.put("phone", rs.getString("phone"));
            row.put("bio", rs.getString("bio"));
            return row;
        }, teacherId);
        result.putAll(profile);
        result.put("courseAssignments", jdbc.query("""
                select c.id course_id,c.name course_name,c.code course_code,c.semester,cta.role_code,cta.subject_or_duty
                from course_teacher_assignment cta
                join course c on c.id=cta.course_id
                where cta.teacher_id=?
                order by c.id
                """, (rs, row) -> Map.of(
                "courseId", rs.getLong("course_id"),
                "courseName", rs.getString("course_name"),
                "courseCode", rs.getString("course_code"),
                "semester", rs.getString("semester"),
                "roleCode", rs.getString("role_code"),
                "subjectOrDuty", rs.getString("subject_or_duty") == null ? "" : rs.getString("subject_or_duty")
        ), teacherId));
        result.put("teachingClassAssignments", jdbc.query("""
                select tc.id teaching_class_id,tc.name teaching_class_name,tc.term,c.id course_id,c.name course_name
                from teaching_class_teacher_assignment tcta
                join teaching_class tc on tc.id=tcta.teaching_class_id
                join course c on c.id=tc.course_id
                where tcta.teacher_id=?
                order by tc.id
                """, (rs, row) -> Map.of(
                "teachingClassId", rs.getLong("teaching_class_id"),
                "teachingClassName", rs.getString("teaching_class_name"),
                "term", rs.getString("term"),
                "courseId", rs.getLong("course_id"),
                "courseName", rs.getString("course_name")
        ), teacherId));
        return result;
    }

    @Transactional
    public Map<String, Object> saveTeacherCourseAssignments(UserAccount actor, long teacherId, Map<String, Object> body) {
        requireTeacherAccount(teacherId);
        List<Map<String, Object>> assignments = mapList(body.get("assignments"));
        jdbc.update("delete from course_teacher_assignment where teacher_id=?", teacherId);
        for (Map<String, Object> assignment : assignments) {
            Long courseId = longValue(assignment.get("courseId"));
            if (courseId == null || !courseExists(courseId)) {
                throw notFound("Course not found");
            }
            String roleCode = normalizeRoleCode(text(assignment, "roleCode", "INSTRUCTOR"));
            String subjectOrDuty = trimToNull(text(assignment, "subjectOrDuty", ""));
            jdbc.update("""
                    insert into course_teacher_assignment(course_id,teacher_id,role_code,subject_or_duty)
                    values (?,?,?,?)
                    """, courseId, teacherId, roleCode, subjectOrDuty);
            if ("LEAD".equals(roleCode)) {
                jdbc.update("update course set teacher_id=? where id=?", teacherId, courseId);
            }
        }
        audit(actor, "SAVE_TEACHER_COURSE_ASSIGNMENTS", "USER", teacherId, "Updated teacher-course assignments");
        return teacherDetail(teacherId);
    }

    @Transactional
    public Map<String, Object> createUser(UserAccount actor, Map<String, Object> body) {
        String loginName = required(body, "loginName", 120);
        if (users.findByLoginName(loginName).isPresent()) {
            throw badRequest("Login name already exists");
        }
        Role role = parseRole(body.get("role"), Role.STUDENT);
        UserAccount user = new UserAccount(
                null,
                loginName,
                auth.encodePassword(text(body, "password", "123456")),
                required(body, "displayName", 80),
                role,
                booleanValue(body.get("enabled"), true)
        );
        user.setEmail(trimToNull(text(body, "email", "")));
        user.setPhone(trimToNull(text(body, "phone", "")));
        user.setBio(trimToNull(text(body, "bio", "")));
        user.setMustChangePassword(booleanValue(body.get("mustChangePassword"), false));
        users.save(user);
        if (role == Role.STUDENT) {
            saveStudentProfile(user.getId(), body, loginName, false);
        } else if (role == Role.TEACHER) {
            saveTeacherProfile(user.getId(), body, false);
        }
        audit(actor, "CREATE_USER", "USER", user.getId(), user.getLoginName());
        return userSnapshot(user.getId());
    }

    @Transactional
    public Map<String, Object> updateUser(UserAccount actor, long id, Map<String, Object> body) {
        UserAccount user = users.findById(id).orElseThrow(() -> notFound("User not found"));
        if (body.containsKey("displayName")) {
            user.setDisplayName(required(body, "displayName", 80));
        }
        if (body.containsKey("email")) {
            user.setEmail(trimToNull(text(body, "email", "")));
        }
        if (body.containsKey("phone")) {
            user.setPhone(trimToNull(text(body, "phone", "")));
        }
        if (body.containsKey("bio")) {
            user.setBio(trimToNull(text(body, "bio", "")));
        }
        users.save(user);
        if (user.getRole() == Role.STUDENT) {
            saveStudentProfile(user.getId(), body, user.getLoginName(), true);
        } else if (user.getRole() == Role.TEACHER) {
            saveTeacherProfile(user.getId(), body, true);
        }
        audit(actor, "UPDATE_USER", "USER", user.getId(), user.getLoginName());
        return userSnapshot(user.getId());
    }

    @Transactional
    public Map<String, Object> setEnabled(UserAccount actor, long id, boolean enabled) {
        UserAccount user = users.findById(id).orElseThrow(() -> notFound("User not found"));
        user.setEnabled(enabled);
        users.save(user);
        if (!enabled) {
            sessions.revokeAll(user.getId());
        }
        audit(actor, enabled ? "ENABLE_USER" : "DISABLE_USER", "USER", user.getId(), user.getLoginName());
        return userSnapshot(user.getId());
    }

    @Transactional
    public void resetPassword(UserAccount actor, long id, String password) {
        if (password == null || password.trim().length() < 6) {
            throw badRequest("Password must be at least 6 characters");
        }
        UserAccount user = users.findById(id).orElseThrow(() -> notFound("User not found"));
        user.setPasswordHash(auth.encodePassword(password.trim()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        users.save(user);
        sessions.revokeAll(user.getId());
        audit(actor, "RESET_PASSWORD", "USER", user.getId(), user.getLoginName());
    }

    @Transactional
    public Map<String, Object> archiveStudent(UserAccount actor, long id) {
        UserAccount user = users.findById(id).orElseThrow(() -> notFound("User not found"));
        if (user.getRole() != Role.STUDENT) {
            throw badRequest("Only student accounts can be archived");
        }
        Map<String, Object> snapshot = studentSnapshot(id);
        Instant now = Instant.now();
        Instant purgeAfter = now.plus(STUDENT_ARCHIVE_RETENTION);
        snapshot.put("snapshotJson", snapshot.toString());
        backups.saveArchivedStudent(snapshot, now, purgeAfter);
        jdbc.update("delete from user_account where id=?", id);
        audit(actor, "ARCHIVE_STUDENT", "USER", id, user.getLoginName());
        return Map.of("archived", true, "purgeAfter", purgeAfter.toString());
    }

    public List<Map<String, Object>> courses() {
        return jdbc.query("""
                select c.id,c.name,c.code,c.class_name,c.semester,c.schedule_text,c.student_count,c.color,
                       c.teacher_id,u.display_name teacher_name
                from course c
                join user_account u on u.id=c.teacher_id
                order by c.id desc
                """, (rs, row) -> {
            long courseId = rs.getLong("id");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", courseId);
            item.put("name", rs.getString("name"));
            item.put("code", rs.getString("code"));
            item.put("className", rs.getString("class_name"));
            item.put("semester", rs.getString("semester"));
            item.put("scheduleText", rs.getString("schedule_text"));
            item.put("studentCount", rs.getInt("student_count"));
            item.put("color", rs.getString("color"));
            item.put("teacherId", rs.getLong("teacher_id"));
            item.put("teacherName", rs.getString("teacher_name"));
            item.put("teachers", courseTeachers(courseId));
            item.put("classes", classes(courseId));
            return item;
        });
    }

    @Transactional
    public Map<String, Object> createCourse(UserAccount actor, Map<String, Object> body) {
        long teacherId = resolveCourseTeacherId(body);
        requireTeacherAccount(teacherId);
        long courseId = insert("""
                insert into course(teacher_id,name,code,class_name,semester,schedule_text,student_count,color)
                values (?,?,?,?,?,?,?,?)
                """,
                teacherId,
                required(body, "name", 120),
                required(body, "code", 40),
                text(body, "className", ""),
                required(body, "semester", 80),
                trimToNull(text(body, "scheduleText", "")),
                integer(body.get("studentCount"), 0),
                text(body, "color", "#07876e")
        );
        ensureCourseTeacherAssignment(courseId, teacherId, "LEAD", trimToNull(text(body, "subjectOrDuty", "")));
        audit(actor, "CREATE_COURSE", "COURSE", courseId, text(body, "name", ""));
        return courseSnapshot(courseId);
    }

    @Transactional
    public Map<String, Object> updateCourse(UserAccount actor, long courseId, Map<String, Object> body) {
        if (!courseExists(courseId)) {
            throw notFound("Course not found");
        }
        Map<String, Object> current = courseSnapshot(courseId);
        Long teacherId = body.containsKey("teacherId") ? longValue(body.get("teacherId")) : longValue(current.get("teacherId"));
        if (teacherId == null) {
            throw badRequest("Teacher is required");
        }
        requireTeacherAccount(teacherId);
        jdbc.update("""
                update course
                set teacher_id=?, name=?, code=?, class_name=?, semester=?, schedule_text=?, student_count=?, color=?
                where id=?
                """,
                teacherId,
                body.containsKey("name") ? required(body, "name", 120) : current.get("name"),
                body.containsKey("code") ? required(body, "code", 40) : current.get("code"),
                body.containsKey("className") ? text(body, "className", "") : current.get("className"),
                body.containsKey("semester") ? required(body, "semester", 80) : current.get("semester"),
                body.containsKey("scheduleText") ? trimToNull(text(body, "scheduleText", "")) : current.get("scheduleText"),
                body.containsKey("studentCount") ? integer(body.get("studentCount"), integer(current.get("studentCount"), 0)) : current.get("studentCount"),
                body.containsKey("color") ? text(body, "color", "#07876e") : current.get("color"),
                courseId
        );
        ensureCourseTeacherAssignment(courseId, teacherId, "LEAD", null);
        audit(actor, "UPDATE_COURSE", "COURSE", courseId, String.valueOf(current.get("name")));
        return courseSnapshot(courseId);
    }

    @Transactional
    public void deleteCourse(UserAccount actor, long courseId) {
        String name = jdbc.query("select name from course where id=?", rs -> rs.next() ? rs.getString(1) : null, courseId);
        if (name == null) {
            throw notFound("Course not found");
        }
        jdbc.update("delete from course where id=?", courseId);
        audit(actor, "DELETE_COURSE", "COURSE", courseId, name);
    }

    public List<Map<String, Object>> classes(long courseId) {
        return jdbc.query("""
                select tc.id,tc.course_id,tc.name,tc.term,tc.enabled,
                       tcta.teacher_id,u.display_name teacher_name
                from teaching_class tc
                left join teaching_class_teacher_assignment tcta on tcta.teaching_class_id=tc.id
                left join user_account u on u.id=tcta.teacher_id
                where tc.course_id=?
                order by tc.id
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("courseId", rs.getLong("course_id"));
            item.put("name", rs.getString("name"));
            item.put("term", rs.getString("term"));
            item.put("enabled", rs.getBoolean("enabled"));
            Object teacherId = rs.getObject("teacher_id");
            item.put("teacherId", teacherId == null ? null : ((Number) teacherId).longValue());
            item.put("teacherName", rs.getString("teacher_name"));
            return item;
        }, courseId);
    }

    @Transactional
    public Map<String, Object> createClass(UserAccount actor, long courseId, Map<String, Object> body) {
        if (!courseExists(courseId)) {
            throw notFound("Course not found");
        }
        long classId = insert("""
                insert into teaching_class(course_id,name,term,enabled)
                values (?,?,?,?)
                """,
                courseId,
                required(body, "name", 120),
                required(body, "term", 80),
                booleanValue(body.get("enabled"), true)
        );
        if (body.containsKey("teacherId") && longValue(body.get("teacherId")) != null) {
            assignTeachingClassTeacher(actor, classId, longValue(body.get("teacherId")));
        }
        audit(actor, "CREATE_TEACHING_CLASS", "TEACHING_CLASS", classId, text(body, "name", ""));
        return teachingClassSnapshot(classId);
    }

    @Transactional
    public Map<String, Object> updateTeachingClass(UserAccount actor, long classId, Map<String, Object> body) {
        Map<String, Object> current = teachingClassSnapshot(classId);
        jdbc.update("""
                update teaching_class
                set name=?, term=?, enabled=?
                where id=?
                """,
                body.containsKey("name") ? required(body, "name", 120) : current.get("name"),
                body.containsKey("term") ? required(body, "term", 80) : current.get("term"),
                body.containsKey("enabled") ? booleanValue(body.get("enabled"), true) : current.get("enabled"),
                classId
        );
        if (body.containsKey("teacherId")) {
            Long teacherId = longValue(body.get("teacherId"));
            if (teacherId == null) {
                jdbc.update("delete from teaching_class_teacher_assignment where teaching_class_id=?", classId);
            } else {
                assignTeachingClassTeacher(actor, classId, teacherId);
            }
        }
        audit(actor, "UPDATE_TEACHING_CLASS", "TEACHING_CLASS", classId, String.valueOf(current.get("name")));
        return teachingClassSnapshot(classId);
    }

    @Transactional
    public void deleteTeachingClass(UserAccount actor, long classId) {
        String name = jdbc.query("select name from teaching_class where id=?", rs -> rs.next() ? rs.getString(1) : null, classId);
        if (name == null) {
            throw notFound("Teaching class not found");
        }
        jdbc.update("delete from teaching_class where id=?", classId);
        audit(actor, "DELETE_TEACHING_CLASS", "TEACHING_CLASS", classId, name);
    }

    @Transactional
    public Map<String, Object> assignTeachingClassTeacher(UserAccount actor, long classId, long teacherId) {
        Map<String, Object> teachingClass = teachingClassSnapshot(classId);
        requireTeacherAccount(teacherId);
        long courseId = ((Number) teachingClass.get("courseId")).longValue();
        ensureCourseTeacherAssignment(courseId, teacherId, "INSTRUCTOR", null);
        jdbc.update("delete from teaching_class_teacher_assignment where teaching_class_id=?", classId);
        jdbc.update("""
                insert into teaching_class_teacher_assignment(teaching_class_id,teacher_id,role_code,subject_or_duty)
                values (?,?,?,?)
                """, classId, teacherId, "INSTRUCTOR", null);
        audit(actor, "ASSIGN_TEACHING_CLASS_TEACHER", "TEACHING_CLASS", classId, String.valueOf(teacherId));
        return teachingClassSnapshot(classId);
    }

    public List<Map<String, Object>> administrativeClasses() {
        return jdbc.query("""
                select id,name,grade_year,major_name,enabled
                from administrative_class
                order by id desc
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("name", rs.getString("name"));
            item.put("gradeYear", rs.getString("grade_year"));
            item.put("majorName", rs.getString("major_name"));
            item.put("enabled", rs.getBoolean("enabled"));
            return item;
        });
    }

    @Transactional
    public Map<String, Object> createAdministrativeClass(UserAccount actor, Map<String, Object> body) {
        long classId = insert("""
                insert into administrative_class(name,grade_year,major_name,enabled)
                values (?,?,?,?)
                """,
                required(body, "name", 120),
                required(body, "gradeYear", 20),
                trimToNull(text(body, "majorName", "")),
                booleanValue(body.get("enabled"), true)
        );
        audit(actor, "CREATE_ADMINISTRATIVE_CLASS", "ADMINISTRATIVE_CLASS", classId, text(body, "name", ""));
        return administrativeClassSnapshot(classId);
    }

    @Transactional
    public Map<String, Object> updateAdministrativeClass(UserAccount actor, long classId, Map<String, Object> body) {
        Map<String, Object> current = administrativeClassSnapshot(classId);
        jdbc.update("""
                update administrative_class
                set name=?, grade_year=?, major_name=?, enabled=?
                where id=?
                """,
                body.containsKey("name") ? required(body, "name", 120) : current.get("name"),
                body.containsKey("gradeYear") ? required(body, "gradeYear", 20) : current.get("gradeYear"),
                body.containsKey("majorName") ? trimToNull(text(body, "majorName", "")) : current.get("majorName"),
                body.containsKey("enabled") ? booleanValue(body.get("enabled"), true) : current.get("enabled"),
                classId
        );
        audit(actor, "UPDATE_ADMINISTRATIVE_CLASS", "ADMINISTRATIVE_CLASS", classId, String.valueOf(current.get("name")));
        return administrativeClassSnapshot(classId);
    }

    @Transactional
    public void deleteAdministrativeClass(UserAccount actor, long classId) {
        Integer count = jdbc.queryForObject("select count(*) from student_profile where administrative_class_id=?", Integer.class, classId);
        if (count != null && count > 0) {
            throw badRequest("Administrative class still has linked students");
        }
        String name = jdbc.query("select name from administrative_class where id=?", rs -> rs.next() ? rs.getString(1) : null, classId);
        if (name == null) {
            throw notFound("Administrative class not found");
        }
        jdbc.update("delete from administrative_class where id=?", classId);
        audit(actor, "DELETE_ADMINISTRATIVE_CLASS", "ADMINISTRATIVE_CLASS", classId, name);
    }

    public Map<String, Object> settings() {
        Map<String, Object> result = new LinkedHashMap<>();
        jdbc.query("select setting_key,setting_value from platform_setting", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getString("setting_key"), rs.getString("setting_value")));
        result.putIfAbsent("platformName", "Zhixuyun");
        result.putIfAbsent("schoolName", "Quanzhou Vocational University of Information Engineering");
        result.putIfAbsent("dataRetentionDays", "365");
        result.put("database", databaseName());
        result.put("sessionHours", 12);
        return result;
    }

    @Transactional
    public Map<String, Object> saveSettings(UserAccount actor, Map<String, Object> body) {
        for (String key : List.of(
                "platformName", "schoolName", "dataRetentionDays",
                "aiApiUrl", "aiModel", "aiTimeoutSeconds", "aiFeatures",
                "aiDailyLimit", "aiRetryCount", "aiAttribution",
                "announcementScope", "announcementLevel", "announcementTitle",
                "announcementContent", "announcementTime", "announcementExpiresAt")) {
            if (body.containsKey(key)) {
                String value = text(body, key, "");
                int updated = jdbc.update("update platform_setting set setting_value=? where setting_key=?", value, key);
                if (updated == 0) {
                    jdbc.update("insert into platform_setting(setting_key,setting_value) values (?,?)", key, value);
                }
            }
        }
        audit(actor, "UPDATE_PLATFORM_SETTINGS", "SETTING", null, "Updated platform settings");
        return settings();
    }

    @Transactional
    public Map<String, Object> importStudents(UserAccount actor, MultipartFile file) {
        List<Map<String, String>> rows;
        try {
            rows = studentImportParser.parse(file);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
        List<Map<String, Object>> created = new ArrayList<>();
        int skipped = 0;
        for (Map<String, String> row : rows) {
            String loginName = firstNonBlank(trimToNull(row.get("loginName")), trimToNull(row.get("studentNo")));
            String displayName = trimToNull(row.get("displayName"));
            String studentNo = firstNonBlank(trimToNull(row.get("studentNo")), loginName);
            if (loginName == null || displayName == null || studentNo == null) {
                skipped++;
                continue;
            }
            if (users.findByLoginName(loginName).isPresent() || studentNoExists(studentNo)) {
                skipped++;
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loginName", loginName);
            payload.put("displayName", displayName);
            payload.put("role", "STUDENT");
            payload.put("password", firstNonBlank(trimToNull(row.get("password")), "123456"));
            payload.put("email", trimToNull(row.get("email")));
            payload.put("studentNo", studentNo);
            payload.put("gradeYear", trimToNull(row.get("gradeYear")));
            payload.put("administrativeClassName", trimToNull(row.get("administrativeClassName")));
            created.add(createUser(actor, payload));
        }
        audit(actor, "IMPORT_STUDENTS", "USER", null, "Imported students in batch");
        return Map.of(
                "createdCount", created.size(),
                "skippedCount", skipped,
                "items", created
        );
    }

    @Transactional
    public Map<String, Object> transferStudent(UserAccount actor, long studentId, Map<String, Object> body) {
        UserAccount user = users.findById(studentId).orElseThrow(() -> notFound("User not found"));
        if (user.getRole() != Role.STUDENT) {
            throw badRequest("Only student accounts can be transferred");
        }
        Long targetClassId = longValue(body.get("administrativeClassId"));
        if (targetClassId == null) {
            throw badRequest("Target administrative class is required");
        }
        Map<String, Object> administrativeClass = administrativeClassSnapshot(targetClassId);
        int updated = jdbc.update("""
                update student_profile
                set administrative_class_id=?, grade_year=?
                where user_id=?
                """, targetClassId, administrativeClass.get("gradeYear"), studentId);
        if (updated == 0) {
            throw badRequest("Student profile not found");
        }
        audit(actor, "TRANSFER_STUDENT", "USER", studentId, "Transfer to class " + targetClassId);
        return userSnapshot(studentId);
    }

    private Map<String, Object> userSnapshot(long userId) {
        return jdbc.query("""
                select u.id,u.login_name,u.display_name,u.role,u.enabled,u.email,u.phone,u.bio,
                       sp.student_no,sp.grade_year,sp.administrative_class_id,ac.name administrative_class_name
                from user_account u
                left join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                where u.id=?
                """, rs -> {
            if (!rs.next()) {
                throw notFound("User not found");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("loginName", rs.getString("login_name"));
            item.put("displayName", rs.getString("display_name"));
            item.put("role", rs.getString("role"));
            item.put("enabled", rs.getBoolean("enabled"));
            item.put("email", rs.getString("email"));
            item.put("phone", rs.getString("phone"));
            item.put("bio", rs.getString("bio"));
            item.put("studentNo", rs.getString("student_no"));
            item.put("gradeYear", rs.getString("grade_year"));
            Object administrativeClassId = rs.getObject("administrative_class_id");
            item.put("administrativeClassId", administrativeClassId == null ? null : ((Number) administrativeClassId).longValue());
            item.put("administrativeClassName", rs.getString("administrative_class_name"));
            return item;
        }, userId);
    }

    private Map<String, Object> courseSnapshot(long courseId) {
        return courses().stream()
                .filter(item -> Objects.equals(((Number) item.get("id")).longValue(), courseId))
                .findFirst()
                .orElseThrow(() -> notFound("Course not found"));
    }

    private Map<String, Object> teachingClassSnapshot(long classId) {
        return jdbc.query("""
                select tc.id,tc.course_id,tc.name,tc.term,tc.enabled,
                       tcta.teacher_id,u.display_name teacher_name
                from teaching_class tc
                left join teaching_class_teacher_assignment tcta on tcta.teaching_class_id=tc.id
                left join user_account u on u.id=tcta.teacher_id
                where tc.id=?
                """, rs -> {
            if (!rs.next()) {
                throw notFound("Teaching class not found");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("courseId", rs.getLong("course_id"));
            item.put("name", rs.getString("name"));
            item.put("term", rs.getString("term"));
            item.put("enabled", rs.getBoolean("enabled"));
            Object teacherId = rs.getObject("teacher_id");
            item.put("teacherId", teacherId == null ? null : ((Number) teacherId).longValue());
            item.put("teacherName", rs.getString("teacher_name"));
            return item;
        }, classId);
    }

    private Map<String, Object> administrativeClassSnapshot(long classId) {
        return jdbc.query("""
                select id,name,grade_year,major_name,enabled
                from administrative_class
                where id=?
                """, rs -> {
            if (!rs.next()) {
                throw notFound("Administrative class not found");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("name", rs.getString("name"));
            item.put("gradeYear", rs.getString("grade_year"));
            item.put("majorName", rs.getString("major_name"));
            item.put("enabled", rs.getBoolean("enabled"));
            return item;
        }, classId);
    }

    private void saveStudentProfile(long userId, Map<String, ?> body, String fallbackLoginName, boolean existing) {
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) body;
        String studentNo = firstNonBlank(trimToNull(textOrNull(values, "studentNo")), fallbackLoginName);
        if (studentNo == null) {
            throw badRequest("Student number is required");
        }
        Map<String, String> inferred = studentNumbers.infer(studentNo);
        String gradeYear = firstNonBlank(trimToNull(textOrNull(values, "gradeYear")), inferred.get("gradeYear"));
        Long administrativeClassId = longValue(values.get("administrativeClassId"));
        if (administrativeClassId == null) {
            administrativeClassId = findAdministrativeClassId(firstNonBlank(trimToNull(textOrNull(values, "administrativeClassName")), inferred.get("administrativeClassName")));
        }
        if (existing) {
            Long owner = jdbc.query("select user_id from student_profile where student_no=?", rs -> rs.next() ? rs.getLong(1) : null, studentNo);
            if (owner != null && owner != userId) {
                throw badRequest("Student number already exists");
            }
            int updated = jdbc.update("""
                    update student_profile
                    set student_no=?, grade_year=?, administrative_class_id=?
                    where user_id=?
                    """, studentNo, gradeYear, administrativeClassId, userId);
            if (updated == 0) {
                jdbc.update("""
                        insert into student_profile(user_id,student_no,grade_year,administrative_class_id)
                        values (?,?,?,?)
                        """, userId, studentNo, gradeYear, administrativeClassId);
            }
            return;
        }
        if (studentNoExists(studentNo)) {
            throw badRequest("Student number already exists");
        }
        jdbc.update("""
                insert into student_profile(user_id,student_no,grade_year,administrative_class_id)
                values (?,?,?,?)
                """, userId, studentNo, gradeYear, administrativeClassId);
    }

    private void saveTeacherProfile(long userId, Map<String, Object> body, boolean existing) {
        String department = text(body, "department", "Software Engineering");
        String title = text(body, "title", "Teacher");
        String email = trimToNull(text(body, "email", ""));
        String phone = trimToNull(text(body, "phone", ""));
        String bio = trimToNull(text(body, "bio", ""));
        if (existing) {
            int updated = jdbc.update("""
                    update teacher_profile
                    set department=?, title=?, email=?, phone=?, bio=?
                    where user_id=?
                    """, department, title, email, phone, bio, userId);
            if (updated > 0) {
                return;
            }
        }
        jdbc.update("""
                insert into teacher_profile(user_id,department,title,email,phone,bio)
                values (?,?,?,?,?,?)
                """, userId, department, title, email, phone, bio);
    }

    private List<Map<String, Object>> courseTeachers(long courseId) {
        return jdbc.query("""
                select u.id,u.display_name,cta.role_code,cta.subject_or_duty
                from course_teacher_assignment cta
                join user_account u on u.id=cta.teacher_id
                where cta.course_id=?
                order by case when cta.role_code='LEAD' then 0 else 1 end, u.id
                """, (rs, row) -> Map.of(
                "teacherId", rs.getLong("id"),
                "teacherName", rs.getString("display_name"),
                "roleCode", rs.getString("role_code"),
                "subjectOrDuty", rs.getString("subject_or_duty") == null ? "" : rs.getString("subject_or_duty")
        ), courseId);
    }

    private Map<String, Object> studentSnapshot(long id) {
        return jdbc.query("""
                select u.id,u.login_name,u.display_name,u.email,sp.student_no,sp.grade_year,ac.name administrative_class_name
                from user_account u
                left join student_profile sp on sp.user_id=u.id
                left join administrative_class ac on ac.id=sp.administrative_class_id
                where u.id=?
                """, rs -> {
            if (!rs.next()) {
                throw notFound("Student not found");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("loginName", rs.getString("login_name"));
            item.put("displayName", rs.getString("display_name"));
            item.put("email", rs.getString("email"));
            item.put("studentNo", rs.getString("student_no"));
            item.put("gradeYear", rs.getString("grade_year"));
            item.put("administrativeClassName", rs.getString("administrative_class_name"));
            return item;
        }, id);
    }

    private Long findAdministrativeClassId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<Long> ids = jdbc.query("select id from administrative_class where name=?", (rs, row) -> rs.getLong("id"), name.trim());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean studentNoExists(String studentNo) {
        Integer count = jdbc.queryForObject("select count(*) from student_profile where student_no=?", Integer.class, studentNo);
        return count != null && count > 0;
    }

    private boolean courseExists(long courseId) {
        Integer count = jdbc.queryForObject("select count(*) from course where id=?", Integer.class, courseId);
        return count != null && count > 0;
    }

    private long resolveCourseTeacherId(Map<String, Object> body) {
        Long teacherId = longValue(body.get("teacherId"));
        if (teacherId != null) {
            return teacherId;
        }
        List<Long> ids = jdbc.query("select id from user_account where role='TEACHER' order by id", (rs, row) -> rs.getLong("id"));
        if (ids.isEmpty()) {
            throw badRequest("Please create a teacher account before creating a course");
        }
        return ids.get(0);
    }

    private void ensureCourseTeacherAssignment(long courseId, long teacherId, String roleCode, String subjectOrDuty) {
        requireTeacherAccount(teacherId);
        Integer count = jdbc.queryForObject("""
                select count(*) from course_teacher_assignment
                where course_id=? and teacher_id=?
                """, Integer.class, courseId, teacherId);
        if (count != null && count > 0) {
            jdbc.update("""
                    update course_teacher_assignment
                    set role_code=?, subject_or_duty=coalesce(?, subject_or_duty)
                    where course_id=? and teacher_id=?
                    """, normalizeRoleCode(roleCode), subjectOrDuty, courseId, teacherId);
            return;
        }
        jdbc.update("""
                insert into course_teacher_assignment(course_id,teacher_id,role_code,subject_or_duty)
                values (?,?,?,?)
                """, courseId, teacherId, normalizeRoleCode(roleCode), subjectOrDuty);
    }

    private UserAccount requireTeacherAccount(long teacherId) {
        UserAccount user = users.findById(teacherId).orElseThrow(() -> notFound("Teacher not found"));
        if (user.getRole() != Role.TEACHER) {
            throw badRequest("Target account is not a teacher");
        }
        return user;
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String databaseName() {
        try {
            return jdbc.getDataSource().getConnection().getMetaData().getDatabaseProductName();
        } catch (Exception ignored) {
            return "Unknown";
        }
    }

    private long insert(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, keys);
        if (keys.getKey() == null) {
            throw new IllegalStateException("Failed to read generated key");
        }
        return keys.getKey().longValue();
    }

    private void audit(UserAccount actor, String action, String type, Long id, String detail) {
        jdbc.update("""
                insert into audit_log(actor_id,action,target_type,target_id,detail,created_at)
                values (?,?,?,?,?,?)
                """, actor.getId(), action, type, id == null ? null : String.valueOf(id), detail, Timestamp.from(Instant.now()));
    }

    private static String like(String value) {
        return "%" + value + "%";
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static String textOrNull(Map<String, Object> body, String key) {
        String value = text(body, key, "");
        return value.isBlank() ? null : value;
    }

    private static String required(Map<String, Object> body, String key, int max) {
        String value = text(body, key, "");
        if (value.isBlank() || value.length() > max) {
            throw badRequest(key + " is required and must be " + max + " characters or fewer");
        }
        return value;
    }

    private static Role parseRole(Object value, Role fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Role.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw badRequest("Invalid role");
        }
    }

    private static String normalizeRoleCode(String roleCode) {
        String normalized = roleCode == null ? "INSTRUCTOR" : roleCode.trim().toUpperCase();
        return "LEAD".equals(normalized) ? "LEAD" : "INSTRUCTOR";
    }

    private static Long longValue(Object value) {
        try {
            return value == null || String.valueOf(value).isBlank() ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int integer(Object value, int fallback) {
        try {
            return value == null || String.valueOf(value).isBlank() ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isBlank() ? null : value.trim();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private static AuthException badRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private static AuthException notFound(String message) {
        return new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
