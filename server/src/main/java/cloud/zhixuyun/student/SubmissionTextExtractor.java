package cloud.zhixuyun.student;

import cloud.zhixuyun.auth.AuthException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class SubmissionTextExtractor {
    private final int maxCharacters;

    public SubmissionTextExtractor(@Value("${zhixuyun.ai.max-submission-characters:30000}") int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public String extract(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (lower.endsWith(".docx")) {
                text = extractDocx(file);
            } else if (lower.endsWith(".pdf")) {
                text = extractPdf(file);
            } else {
                throw invalidFile("目前仅支持 .txt、.md 和 .docx 格式的报告");
            }
            text = text.replace("\uFEFF", "").trim();
            if (text.isBlank()) throw invalidFile("报告文件中没有可批改的正文");
            validateLength(text);
            return text;
        } catch (AuthException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidFile("无法读取报告文件，请检查文件是否完整");
        }
    }

    public void validateLength(String text) {
        if (text.length() > maxCharacters) {
            throw invalidFile("报告正文过长，最多允许 " + maxCharacters + " 个字符");
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            String paragraphs = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText).filter(value -> !value.isBlank())
                    .collect(Collectors.joining("\n"));
            String tables = document.getTables().stream().map(XWPFTable::getText)
                    .filter(value -> !value.isBlank()).collect(Collectors.joining("\n"));
            return paragraphs + (paragraphs.isBlank() || tables.isBlank() ? "" : "\n") + tables;
        }
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (var document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static AuthException invalidFile(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_SUBMISSION_FILE", message);
    }
}
