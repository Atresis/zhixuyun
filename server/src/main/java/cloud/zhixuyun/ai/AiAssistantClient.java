package cloud.zhixuyun.ai;

import cloud.zhixuyun.auth.AuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiAssistantClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final double temperature;
    private final int maxTokens;

    public AiAssistantClient(ObjectMapper json,
                             @Value("${zhixuyun.ai.api-key:}") String apiKey,
                             @Value("${zhixuyun.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                             @Value("${zhixuyun.ai.model:kimi-k2.6}") String model,
                             @Value("${zhixuyun.ai.timeout:180s}") Duration timeout,
                             @Value("${zhixuyun.ai.temperature:1}") double temperature,
                             @Value("${zhixuyun.ai.max-tokens:4096}") int maxTokens) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.timeout = timeout;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String complete(String systemPrompt, List<Map<String, String>> messages) {
        if (apiKey.isBlank()) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                "AI 助手尚未配置模型 API Key，请设置 ZHIXUYUN_AI_API_KEY");
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", systemPrompt));
        requestMessages.addAll(messages);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", requestMessages);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                        root.path("error").path("message").asText("模型服务请求失败"));
            }
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (answer.isBlank()) throw new AuthException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "模型未返回有效内容");
            return answer;
        } catch (AuthException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "模型请求被中断，请稍后重试");
        } catch (IOException | RuntimeException exception) {
            throw new AuthException(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "模型服务暂时不可用，请稍后重试");
        }
    }
}
