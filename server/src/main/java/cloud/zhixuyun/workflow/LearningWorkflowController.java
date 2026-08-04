package cloud.zhixuyun.workflow;

import cloud.zhixuyun.auth.Role;
import cloud.zhixuyun.auth.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LearningWorkflowController {
    private final LearningWorkflowService service;

    public LearningWorkflowController(LearningWorkflowService service) { this.service = service; }

    @PostMapping("/student/courses/join")
    public Map<String, Object> joinCourse(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, String> body) {
        return service.joinCourse(service.require(authorization, Role.STUDENT), body.get("code"));
    }

    @GetMapping("/submissions/{submissionId}/versions")
    public List<Map<String, Object>> versions(@RequestHeader("Authorization") String authorization, @PathVariable long submissionId) {
        return service.submissionVersions(service.current(authorization), submissionId);
    }

    @GetMapping("/teacher/courses/{courseId}/invite-code")
    public Map<String, Object> inviteCode(@RequestHeader("Authorization") String authorization, @PathVariable long courseId) {
        return service.inviteCode(service.require(authorization, Role.TEACHER), courseId);
    }

    @PostMapping("/teacher/courses/{courseId}/invite-code")
    public Map<String, Object> regenerateInviteCode(@RequestHeader("Authorization") String authorization, @PathVariable long courseId) {
        return service.regenerateInviteCode(service.require(authorization, Role.TEACHER), courseId);
    }

    @PostMapping("/teacher/submissions/{submissionId}/return")
    public Map<String, Object> returnSubmission(@RequestHeader("Authorization") String authorization, @PathVariable long submissionId,
                                                 @RequestBody Map<String, String> body) {
        return service.returnSubmission(service.require(authorization, Role.TEACHER), submissionId, body.get("reason"));
    }

    @GetMapping("/teacher/tasks/{taskId}/analytics")
    public Map<String, Object> analytics(@RequestHeader("Authorization") String authorization, @PathVariable long taskId) {
        return service.analytics(service.require(authorization, Role.TEACHER), taskId);
    }

    @GetMapping("/teacher/rubrics")
    public List<Map<String, Object>> rubrics(@RequestHeader("Authorization") String authorization) {
        return service.rubrics(service.require(authorization, Role.TEACHER));
    }

    @PostMapping("/teacher/rubrics")
    public Map<String, Object> createRubric(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        return service.createRubric(service.require(authorization, Role.TEACHER), body);
    }

    @PutMapping("/teacher/rubrics/{id}")
    public Map<String, Object> updateRubric(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                            @RequestBody Map<String, Object> body) {
        return service.updateRubric(service.require(authorization, Role.TEACHER), id, body);
    }

    @PatchMapping("/teacher/rubrics/{id}/status")
    public Map<String, Object> rubricStatus(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                            @RequestBody Map<String, Boolean> body) {
        return service.setRubricEnabled(service.require(authorization, Role.TEACHER), id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications(@RequestHeader("Authorization") String authorization) {
        return service.notifications(service.current(authorization));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        service.markNotificationRead(service.current(authorization), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Object> readAllNotifications(@RequestHeader("Authorization") String authorization) {
        UserAccount user = service.current(authorization);
        return Map.of("updated", service.markAllNotificationsRead(user));
    }
}
