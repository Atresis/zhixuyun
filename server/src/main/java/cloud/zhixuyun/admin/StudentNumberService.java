package cloud.zhixuyun.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StudentNumberService {
    private static final Pattern STUDENT_NUMBER = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})$");
    private static final Pattern CLASS_NAME = Pattern.compile("(\\d{1,2})班(?:$|\\D)");
    private final JdbcTemplate jdbc;

    public StudentNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> infer(String studentNo) {
        Map<String, String> result = new LinkedHashMap<>();
        if (studentNo == null || studentNo.isBlank()) return result;
        ParsedNumber parsed = parse(studentNo.trim());
        if (parsed == null) return result;
        result.put("status", "NO_MATCH");
        result.put("format", parsed.format());
        result.put("gradeYear", parsed.gradeYear());
        result.put("majorCode", parsed.majorCode());
        result.put("classCode", parsed.classCode());
        if (parsed.collegeCode() != null) result.put("collegeCode", parsed.collegeCode());

        List<ClassCandidate> candidates = jdbc.query("""
                select id,name,college_code,major_code,class_code
                from administrative_class
                where grade_year=? and enabled=true
                order by id
                """, (rs, row) -> new ClassCandidate(
                rs.getLong("id"), rs.getString("name"), rs.getString("college_code"),
                rs.getString("major_code"), rs.getString("class_code")), parsed.gradeYear());

        List<ClassCandidate> exact = candidates.stream().filter(item -> item.exactlyMatches(parsed)).toList();
        if (exact.size() == 1) {
            putMatch(result, exact.get(0));
            return result;
        }
        if (exact.size() > 1) {
            result.put("status", "AMBIGUOUS");
            return result;
        }

        List<ClassCandidate> compatible = candidates.stream().filter(item -> item.compatiblyMatches(parsed)).toList();
        if (compatible.size() == 1) {
            putMatch(result, compatible.get(0));
        } else if (compatible.size() > 1) {
            result.put("status", "AMBIGUOUS");
        }
        return result;
    }

    public String defaultPassword(String studentNo) {
        String normalized = studentNo == null ? "" : studentNo.trim();
        if (!STUDENT_NUMBER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Student number must contain exactly 10 digits");
        }
        return normalized.substring(normalized.length() - 6);
    }

    private ParsedNumber parse(String value) {
        Matcher studentNumber = STUDENT_NUMBER.matcher(value);
        if (studentNumber.matches()) {
            return new ParsedNumber(
                    "YYAAMMCCSS",
                    "20" + studentNumber.group(1),
                    studentNumber.group(2),
                    studentNumber.group(3),
                    studentNumber.group(4)
            );
        }
        return null;
    }

    private void putMatch(Map<String, String> result, ClassCandidate candidate) {
        result.put("status", "MATCHED");
        result.put("administrativeClassId", String.valueOf(candidate.id()));
        result.put("administrativeClassName", candidate.name());
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return String.format("%02d", Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return value.trim();
        }
    }

    private record ParsedNumber(String format, String gradeYear, String collegeCode, String majorCode, String classCode) {}

    private record ClassCandidate(long id, String name, String collegeCode, String majorCode, String classCode) {
        boolean exactlyMatches(ParsedNumber parsed) {
            if (!parsed.classCode().equals(normalizeCode(classCode)) || !parsed.majorCode().equals(normalizeCode(majorCode))) {
                return false;
            }
            return parsed.collegeCode() == null || parsed.collegeCode().equals(normalizeCode(collegeCode));
        }

        boolean compatiblyMatches(ParsedNumber parsed) {
            String candidateClassCode = normalizeCode(classCode);
            if (candidateClassCode == null) {
                Matcher matcher = CLASS_NAME.matcher(name == null ? "" : name);
                candidateClassCode = matcher.find() ? normalizeCode(matcher.group(1)) : null;
            }
            if (!parsed.classCode().equals(candidateClassCode)) return false;
            String candidateMajorCode = normalizeCode(majorCode);
            if (candidateMajorCode != null && !parsed.majorCode().equals(candidateMajorCode)) return false;
            String candidateCollegeCode = normalizeCode(collegeCode);
            return parsed.collegeCode() == null || candidateCollegeCode == null || parsed.collegeCode().equals(candidateCollegeCode);
        }
    }
}
