package cloud.zhixuyun.student;

import org.springframework.http.MediaType;
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

    @PostMapping("/assistant/ask")
    public Map<String, Object> ask(@RequestHeader("Authorization") String authorization,
                                   @RequestBody Map<String, String> body) {
        return service.askAssistant(service.requireStudent(authorization), body.get("content"));
    }
}
