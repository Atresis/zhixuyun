package cloud.zhixuyun.teacher;

import cloud.zhixuyun.auth.UserAccount;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController {
    private final TeacherService service;

    public TeacherController(TeacherService service) { this.service = service; }

    @GetMapping("/workspace")
    public Map<String, Object> workspace(@RequestHeader("Authorization") String authorization) {
        return service.workspace(service.requireTeacher(authorization));
    }

    @PatchMapping("/profile")
    public Map<String, Object> updateProfile(@RequestHeader("Authorization") String authorization,
                                             @RequestBody Map<String, Object> body) {
        return service.updateProfile(service.requireTeacher(authorization), body);
    }

    @PostMapping(value = "/courses/{courseId}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestHeader("Authorization") String authorization,
                                      @PathVariable long courseId,
                                      @RequestParam(defaultValue = "MATERIAL") String kind,
                                      @RequestParam(defaultValue = "false") boolean shared,
                                      @RequestPart("file") MultipartFile file) {
        return service.addResource(service.requireTeacher(authorization), courseId, kind, shared, file);
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<byte[]> download(@RequestHeader("Authorization") String authorization, @PathVariable long resourceId) {
        TeacherService.Download file = service.downloadResource(service.requireTeacher(authorization), resourceId);
        MediaType type;
        try { type = file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType()); }
        catch (IllegalArgumentException ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @GetMapping("/resources/question-bank-template")
    public ResponseEntity<byte[]> questionBankTemplate(@RequestHeader("Authorization") String authorization) {
        service.requireTeacher(authorization);
        byte[] template = "题型,题目,选项(每行一个),答案,分值\n单选题,示例题,A\\nB\\nC\\nD,A,10\n".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("知序云题库模板.csv", StandardCharsets.UTF_8).build().toString())
                .body(template);
    }

    @DeleteMapping("/resources/{resourceId}")
    public ResponseEntity<Void> deleteResource(@RequestHeader("Authorization") String authorization, @PathVariable long resourceId) {
        service.deleteResource(service.requireTeacher(authorization), resourceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/courses/{courseId}/tasks")
    public Map<String, Object> createTask(@RequestHeader("Authorization") String authorization, @PathVariable long courseId,
                                          @RequestBody Map<String, Object> body) {
        return service.createTask(service.requireTeacher(authorization), courseId, body);
    }

    @PatchMapping("/tasks/{taskId}")
    public Map<String, Object> updateTask(@RequestHeader("Authorization") String authorization, @PathVariable long taskId,
                                          @RequestBody Map<String, Object> body) {
        return service.updateTask(service.requireTeacher(authorization), taskId, body);
    }

    @PutMapping("/tasks/{taskId}/questions")
    public Map<String, Object> updateQuestions(@RequestHeader("Authorization") String authorization, @PathVariable long taskId,
                                               @RequestBody Object questions) {
        return service.updateQuestions(service.requireTeacher(authorization), taskId, questions);
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public Map<String, Object> grade(@RequestHeader("Authorization") String authorization, @PathVariable long submissionId,
                                     @RequestBody Map<String, Object> body) {
        return service.gradeSubmission(service.requireTeacher(authorization), submissionId, body);
    }

    @PatchMapping("/alerts/{alertId}/read")
    public Map<String, Object> readAlert(@RequestHeader("Authorization") String authorization, @PathVariable long alertId) {
        return service.markAlertRead(service.requireTeacher(authorization), alertId);
    }

    @PutMapping("/alerts/{alertId}/proposal")
    public Map<String, Object> proposal(@RequestHeader("Authorization") String authorization, @PathVariable long alertId,
                                        @RequestBody Map<String, String> body) {
        return service.saveProposal(service.requireTeacher(authorization), alertId, body.get("proposal"));
    }

    @PostMapping("/assistant/sessions")
    public Map<String, Object> newSession(@RequestHeader("Authorization") String authorization) {
        return service.newAssistantSession(service.requireTeacher(authorization));
    }

    @PostMapping("/assistant/sessions/{sessionId}/messages")
    public Map<String, Object> assistantMessage(@RequestHeader("Authorization") String authorization, @PathVariable long sessionId,
                                                @RequestBody Map<String, String> body) {
        return service.sendAssistantMessage(service.requireTeacher(authorization), sessionId, body.get("content"));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    public Map<String, Object> readConversation(@RequestHeader("Authorization") String authorization, @PathVariable long conversationId) {
        return service.readConversation(service.requireTeacher(authorization), conversationId);
    }

    @GetMapping("/contact-candidates")
    public Map<String, Object> contactCandidates(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam long courseId,
                                                  @RequestParam(defaultValue = "") String q,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return service.contactCandidates(service.requireTeacher(authorization), courseId, q, page, size);
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestHeader("Authorization") String authorization,
                                                  @RequestBody Map<String, Object> body) {
        Object rawStudentId = body.get("studentId");
        if (rawStudentId == null) throw new cloud.zhixuyun.auth.AuthException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请选择学生");
        return service.createConversation(service.requireTeacher(authorization), Long.parseLong(String.valueOf(rawStudentId)),
                body.get("content") == null ? null : String.valueOf(body.get("content")));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> sendMessage(@RequestHeader("Authorization") String authorization, @PathVariable long conversationId,
                                           @RequestBody Map<String, String> body) {
        return service.sendConversationMessage(service.requireTeacher(authorization), conversationId, body.get("content"));
    }
}
