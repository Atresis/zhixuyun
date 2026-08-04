package cloud.zhixuyun.ai;

public interface AiGradingService {
    GradeResult grade(GradeRequest request);

    record GradeRequest(String taskType, String taskName, String taskDescription,
                        int maxScore, String questionsJson, String submissionText) {}

    record GradeResult(int score, String review) {}
}
