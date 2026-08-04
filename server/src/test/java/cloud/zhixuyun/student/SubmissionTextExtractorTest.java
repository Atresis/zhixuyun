package cloud.zhixuyun.student;

import cloud.zhixuyun.auth.AuthException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmissionTextExtractorTest {
    private final SubmissionTextExtractor extractor = new SubmissionTextExtractor(1000);

    @Test
    void extractsDocxParagraphs() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("实验步骤完整");
            document.createParagraph().createRun().setText("结果符合预期");
            document.write(output);
            content = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        assertEquals("实验步骤完整\n结果符合预期", extractor.extract(file));
    }

    @Test
    void rejectsUnsupportedFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf".getBytes());

        assertThrows(AuthException.class, () -> extractor.extract(file));
    }
}
