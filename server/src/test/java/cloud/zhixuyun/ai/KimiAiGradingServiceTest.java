package cloud.zhixuyun.ai;

import cloud.zhixuyun.auth.AuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KimiAiGradingServiceTest {
    private final AiAssistantClient client = mock(AiAssistantClient.class);
    private final KimiAiGradingService service = new KimiAiGradingService(client, new ObjectMapper());

    @Test
    void parsesStructuredGrade() {
        when(client.complete(anyString(), org.mockito.ArgumentMatchers.<List<Map<String, String>>>any()))
                .thenReturn("{\"score\":82,\"review\":\"实现完整，建议补充异常场景测试。\"}");

        AiGradingService.GradeResult result = service.grade(request(100));

        assertEquals(82, result.score());
        assertEquals("实现完整，建议补充异常场景测试。", result.review());
    }

    @Test
    void clampsScoreToTaskMaximum() {
        when(client.complete(anyString(), org.mockito.ArgumentMatchers.<List<Map<String, String>>>any()))
                .thenReturn("```json\n{\"score\":120,\"review\":\"完成度较高。\"}\n```");

        assertEquals(60, service.grade(request(60)).score());
    }

    @Test
    void rejectsMalformedGrade() {
        when(client.complete(anyString(), org.mockito.ArgumentMatchers.<List<Map<String, String>>>any())).thenReturn("评分完成");

        assertThrows(AuthException.class, () -> service.grade(request(100)));
    }

    private static AiGradingService.GradeRequest request(int maxScore) {
        return new AiGradingService.GradeRequest("EXPERIMENT", "数据库实验", "提交结果分析",
                maxScore, "[]", "完成了连接池配置并记录测试结果");
    }
}
