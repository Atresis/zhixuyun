package cloud.zhixuyun.ai;

import cloud.zhixuyun.auth.AuthException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KimiAiGradingService implements AiGradingService {
    private static final String SYSTEM_PROMPT = """
            你是高校课程作业批改助手。请依据任务要求和学生提交内容客观评分，不得因学生身份、姓名或学号调整分数。
            只返回一个 JSON 对象，不要使用 Markdown 代码块，也不要输出 JSON 之外的内容：
            {"score":整数分数,"review":"具体、可执行的中文评语"}
            分数必须位于 0 到任务满分之间。评语应说明完成情况、主要优点、明确问题和修改建议；证据不足时应相应扣分，不得编造提交中不存在的内容。
            """;

    private final AiAssistantClient client;
    private final ObjectMapper json;

    public KimiAiGradingService(AiAssistantClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        String prompt = """
                任务类型：%s
                任务名称：%s
                任务要求：%s
                满分：%d
                题目与参考答案：%s

                学生提交内容：
                %s
                """.formatted(
                safe(request.taskType()), safe(request.taskName()), safe(request.taskDescription()),
                request.maxScore(), safe(request.questionsJson()), safe(request.submissionText()));
        String response = client.complete(SYSTEM_PROMPT, List.of(Map.of("role", "user", "content", prompt)));
        return parse(response, request.maxScore());
    }

    private GradeResult parse(String response, int maxScore) {
        String raw = response == null ? "" : response.trim();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) throw invalidResponse();
        try {
            JsonNode result = json.readTree(raw.substring(start, end + 1));
            if (!result.path("score").canConvertToInt()) throw invalidResponse();
            String review = result.path("review").asText("").trim();
            if (review.isBlank()) throw invalidResponse();
            int score = Math.max(0, Math.min(maxScore, result.path("score").asInt()));
            return new GradeResult(score, review);
        } catch (JsonProcessingException exception) {
            throw invalidResponse();
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value.trim();
    }

    private static AuthException invalidResponse() {
        return new AuthException(HttpStatus.BAD_GATEWAY, "AI_INVALID_GRADE", "模型未返回有效的评分结果，请稍后重试");
    }
}
