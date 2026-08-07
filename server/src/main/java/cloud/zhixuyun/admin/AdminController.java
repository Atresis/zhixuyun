package cloud.zhixuyun.admin;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader("Authorization") String authorization) {
        return service.dashboard(service.requireAdmin(authorization));
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestHeader("Authorization") String authorization,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "") String keyword) {
        service.requireAdmin(authorization);
        return service.logs(page, size, keyword);
    }

    @GetMapping("/users")
    public Map<String, Object> users(@RequestHeader("Authorization") String authorization,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "") String role) {
        service.requireAdmin(authorization);
        return service.userSearch(page, size, keyword, role);
    }

    @GetMapping("/teachers")
    public Map<String, Object> teachers(@RequestHeader("Authorization") String authorization,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(defaultValue = "") String keyword) {
        service.requireAdmin(authorization);
        return Map.of("content", service.teachers(page, size, keyword));
    }

    @GetMapping("/teachers/{teacherId}")
    public Map<String, Object> teacherDetail(@RequestHeader("Authorization") String authorization, @PathVariable long teacherId) {
        service.requireAdmin(authorization);
        return service.teacherDetail(teacherId);
    }

    @PutMapping("/teachers/{teacherId}/courses")
    public Map<String, Object> saveTeacherCourses(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable long teacherId,
                                                  @RequestBody Map<String, Object> body) {
        return service.saveTeacherCourseAssignments(service.requireAdmin(authorization), teacherId, body);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        return service.createUser(service.requireAdmin(authorization), body);
    }

    @PatchMapping("/users/{id}")
    public Map<String, Object> updateUser(@RequestHeader("Authorization") String authorization,
                                          @PathVariable long id,
                                          @RequestBody Map<String, Object> body) {
        return service.updateUser(service.requireAdmin(authorization), id, body);
    }

    @PatchMapping("/users/{id}/status")
    public Map<String, Object> status(@RequestHeader("Authorization") String authorization,
                                      @PathVariable long id,
                                      @RequestBody Map<String, Object> body) {
        return service.setEnabled(service.requireAdmin(authorization), id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", true))));
    }

    @PatchMapping("/users/{id}/password")
    public ResponseEntity<Void> password(@RequestHeader("Authorization") String authorization,
                                         @PathVariable long id,
                                         @RequestBody Map<String, Object> body) {
        service.resetPassword(service.requireAdmin(authorization), id, String.valueOf(body.getOrDefault("newPassword", "")));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/archive")
    public Map<String, Object> archiveStudent(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return service.archiveStudent(service.requireAdmin(authorization), id);
    }

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importStudents(@RequestHeader("Authorization") String authorization,
                                              @RequestPart("file") MultipartFile file) {
        return service.importStudents(service.requireAdmin(authorization), file);
    }

    @PutMapping("/users/{id}/transfer")
    public Map<String, Object> transferStudent(@RequestHeader("Authorization") String authorization,
                                               @PathVariable long id,
                                               @RequestBody Map<String, Object> body) {
        return service.transferStudent(service.requireAdmin(authorization), id, body);
    }

    @GetMapping("/courses")
    public Map<String, Object> courses(@RequestHeader("Authorization") String authorization) {
        service.requireAdmin(authorization);
        return Map.of("items", service.courses());
    }

    @PostMapping("/courses")
    public Map<String, Object> createCourse(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        return service.createCourse(service.requireAdmin(authorization), body);
    }

    @PutMapping("/courses/{courseId}")
    public Map<String, Object> updateCourse(@RequestHeader("Authorization") String authorization,
                                            @PathVariable long courseId,
                                            @RequestBody Map<String, Object> body) {
        return service.updateCourse(service.requireAdmin(authorization), courseId, body);
    }

    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<Void> deleteCourse(@RequestHeader("Authorization") String authorization, @PathVariable long courseId) {
        service.deleteCourse(service.requireAdmin(authorization), courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/classes")
    public Map<String, Object> classes(@RequestHeader("Authorization") String authorization, @PathVariable long courseId) {
        service.requireAdmin(authorization);
        return Map.of("items", service.classes(courseId));
    }

    @PostMapping("/courses/{courseId}/classes")
    public Map<String, Object> createClass(@RequestHeader("Authorization") String authorization,
                                           @PathVariable long courseId,
                                           @RequestBody Map<String, Object> body) {
        return service.createClass(service.requireAdmin(authorization), courseId, body);
    }

    @PutMapping("/teaching-classes/{classId}")
    public Map<String, Object> updateTeachingClass(@RequestHeader("Authorization") String authorization,
                                                   @PathVariable long classId,
                                                   @RequestBody Map<String, Object> body) {
        return service.updateTeachingClass(service.requireAdmin(authorization), classId, body);
    }

    @DeleteMapping("/teaching-classes/{classId}")
    public ResponseEntity<Void> deleteTeachingClass(@RequestHeader("Authorization") String authorization, @PathVariable long classId) {
        service.deleteTeachingClass(service.requireAdmin(authorization), classId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/classes/{classId}/teacher")
    public Map<String, Object> assignClassTeacher(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable long classId,
                                                  @RequestBody Map<String, Object> body) {
        Object teacherId = body.get("teacherId");
        return service.assignTeachingClassTeacher(service.requireAdmin(authorization), classId, Long.parseLong(String.valueOf(teacherId)));
    }

    @GetMapping("/administrative-classes")
    public Map<String, Object> administrativeClasses(@RequestHeader("Authorization") String authorization) {
        service.requireAdmin(authorization);
        return Map.of("items", service.administrativeClasses());
    }

    @PostMapping("/administrative-classes")
    public Map<String, Object> createAdministrativeClass(@RequestHeader("Authorization") String authorization,
                                                         @RequestBody Map<String, Object> body) {
        return service.createAdministrativeClass(service.requireAdmin(authorization), body);
    }

    @PutMapping("/administrative-classes/{classId}")
    public Map<String, Object> updateAdministrativeClass(@RequestHeader("Authorization") String authorization,
                                                         @PathVariable long classId,
                                                         @RequestBody Map<String, Object> body) {
        return service.updateAdministrativeClass(service.requireAdmin(authorization), classId, body);
    }

    @DeleteMapping("/administrative-classes/{classId}")
    public ResponseEntity<Void> deleteAdministrativeClass(@RequestHeader("Authorization") String authorization, @PathVariable long classId) {
        service.deleteAdministrativeClass(service.requireAdmin(authorization), classId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings(@RequestHeader("Authorization") String authorization) {
        service.requireAdmin(authorization);
        return service.settings();
    }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        return service.saveSettings(service.requireAdmin(authorization), body);
    }

    @PostMapping("/announcements")
    public Map<String, Object> publishAnnouncement(@RequestHeader("Authorization") String authorization,
                                                    @RequestBody Map<String, Object> body) {
        return service.publishAnnouncement(service.requireAdmin(authorization), body);
    }
}
