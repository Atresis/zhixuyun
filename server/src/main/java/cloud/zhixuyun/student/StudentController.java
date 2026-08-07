package cloud.zhixuyun.student;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/student")
public class StudentController {
    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    public Map<String, Object> workspace(@RequestHeader("Authorization") String authorization) {
        return service.workspace(service.requireStudent(authorization));
    }

    @PostMapping("/tasks/{taskId}/text-submission")
    public Map<String, Object> submitText(@RequestHeader("Authorization") String authorization,
                                          @PathVariable long taskId,
                                          @RequestBody Map<String, String> body) {
        return service.submitText(service.requireStudent(authorization), taskId, body.get("content"));
    }

    @PostMapping(value = "/tasks/{taskId}/file-submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> submitFile(@RequestHeader("Authorization") String authorization,
                                          @PathVariable long taskId,
                                          @RequestPart("file") MultipartFile file) {
        return service.submitFile(service.requireStudent(authorization), taskId, file);
    }

    @PostMapping("/tasks/{taskId}/answer-submission")
    public Map<String, Object> submitAnswers(@RequestHeader("Authorization") String authorization,
                                             @PathVariable long taskId,
                                             @RequestBody Map<String, Object> body) {
        return service.submitAnswers(service.requireStudent(authorization), taskId, body.get("answers"));
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<byte[]> downloadResource(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable long resourceId) {
        StudentService.Download file = service.downloadResource(service.requireStudent(authorization), resourceId);
        MediaType type;
        try { type = file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType()); }
        catch (IllegalArgumentException ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @PostMapping("/assistant/ask")
    public Map<String, Object> ask(@RequestHeader("Authorization") String authorization,
                                   @RequestBody Map<String, String> body) {
        return service.askAssistant(service.requireStudent(authorization), body.get("content"));
    }

    @PostMapping("/assistant/sessions")
    public Map<String, Object> createAssistantSession(@RequestHeader("Authorization") String authorization) {
        return service.createAssistantSession(service.requireStudent(authorization));
    }

    @PostMapping("/assistant/sessions/{sessionId}/messages")
    public Map<String, Object> sendAssistantMessage(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable long sessionId,
                                                    @RequestBody Map<String, String> body) {
        return service.sendAssistantMessage(service.requireStudent(authorization), sessionId, body.get("content"));
    }

    @GetMapping("/teacher-contacts")
    public Map<String, Object> teacherContacts(@RequestHeader("Authorization") String authorization) {
        return Map.of("items", service.teacherContacts(service.requireStudent(authorization)));
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestHeader("Authorization") String authorization,
                                                  @RequestBody Map<String, Object> body) {
        return service.createConversation(service.requireStudent(authorization),
                ((Number) body.getOrDefault("teacherId", 0)).longValue(), String.valueOf(body.getOrDefault("content", "")));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> sendConversationMessage(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable long conversationId,
                                                       @RequestBody Map<String, String> body) {
        return service.sendConversationMessage(service.requireStudent(authorization), conversationId, body.get("content"));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public Map<String, Object> readConversation(@RequestHeader("Authorization") String authorization,
                                                @PathVariable long conversationId) {
        return service.readConversation(service.requireStudent(authorization), conversationId);
    }
}
