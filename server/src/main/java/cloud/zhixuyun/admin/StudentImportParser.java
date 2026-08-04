package cloud.zhixuyun.admin;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StudentImportParser {
    private static final List<Charset> CSV_CHARSETS = List.of(StandardCharsets.UTF_8, Charset.forName("GBK"));
    private static final List<String> STUDENT_NO_HEADERS = List.of("studentno", "student_no", "学号");
    private static final List<String> DISPLAY_NAME_HEADERS = List.of("displayname", "display_name", "name", "姓名", "学生姓名");
    private static final List<String> LOGIN_NAME_HEADERS = List.of("loginname", "login_name", "account", "账号", "登录账号");
    private static final List<String> EMAIL_HEADERS = List.of("email", "邮箱", "mail");
    private static final List<String> GRADE_YEAR_HEADERS = List.of("gradeyear", "grade_year", "grade", "年级");
    private static final List<String> CLASS_HEADERS = List.of("administrativeclassname", "administrative_class_name", "classname", "class_name", "class", "班级", "行政班");
    private static final List<String> PASSWORD_HEADERS = List.of("password", "密码");

    public List<Map<String, String>> parse(MultipartFile file) {
        String filename = file == null ? "" : String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        try {
            if (filename.endsWith(".xlsx")) {
                return parseXlsx(file);
            }
            if (filename.endsWith(".csv")) {
                return parseCsv(file);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to parse import file");
        }
        throw new IllegalArgumentException("Only .xlsx and .csv files are supported");
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) throws IOException {
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            var sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return List.of();
            }
            Map<String, Integer> header = readHeader(sheet.getRow(sheet.getFirstRowNum()), formatter);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                rows.add(readValues(header, column -> formatter.formatCellValue(row.getCell(column))));
            }
            return rows;
        }
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        IOException failure = null;
        for (Charset charset : CSV_CHARSETS) {
            try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset))) {
                List<String> lines = reader.lines().toList();
                if (lines.isEmpty()) {
                    return List.of();
                }
                Map<String, Integer> header = readHeader(splitCsvLine(lines.get(0)));
                List<Map<String, String>> rows = new ArrayList<>();
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    List<String> columns = splitCsvLine(line);
                    rows.add(readValues(header, column -> column < columns.size() ? columns.get(column) : ""));
                }
                return rows;
            } catch (IOException exception) {
                failure = exception;
            }
        }
        throw failure == null ? new IOException("Failed to parse CSV") : failure;
    }

    private Map<String, Integer> readHeader(Row row, DataFormatter formatter) {
        Map<String, Integer> header = new LinkedHashMap<>();
        short lastCell = row == null ? 0 : row.getLastCellNum();
        for (int i = 0; i < Math.max(lastCell, 0); i++) {
            Cell cell = row.getCell(i);
            header.put(normalizeHeader(formatter.formatCellValue(cell)), i);
        }
        return header;
    }

    private Map<String, Integer> readHeader(List<String> columns) {
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            header.put(normalizeHeader(columns.get(i)), i);
        }
        return header;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (!formatter.formatCellValue(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> readValues(Map<String, Integer> header, java.util.function.IntFunction<String> valueReader) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("studentNo", readColumn(header, STUDENT_NO_HEADERS, valueReader));
        item.put("displayName", readColumn(header, DISPLAY_NAME_HEADERS, valueReader));
        item.put("loginName", readColumn(header, LOGIN_NAME_HEADERS, valueReader));
        item.put("email", readColumn(header, EMAIL_HEADERS, valueReader));
        item.put("gradeYear", readColumn(header, GRADE_YEAR_HEADERS, valueReader));
        item.put("administrativeClassName", readColumn(header, CLASS_HEADERS, valueReader));
        item.put("password", readColumn(header, PASSWORD_HEADERS, valueReader));
        return item;
    }

    private String readColumn(Map<String, Integer> header, List<String> aliases, java.util.function.IntFunction<String> valueReader) {
        for (String alias : aliases) {
            Integer index = header.get(normalizeHeader(alias));
            if (index != null) {
                String value = valueReader.apply(index);
                return value == null ? "" : value.trim();
            }
        }
        return "";
    }

    private List<String> splitCsvLine(String line) {
        String delimiter = line.contains("\t") ? "\t" : ",";
        String[] raw = line.split("\\" + delimiter, -1);
        List<String> result = new ArrayList<>(raw.length);
        for (String column : raw) {
            result.add(column == null ? "" : column.replace("\uFEFF", "").trim());
        }
        return result;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }
}
