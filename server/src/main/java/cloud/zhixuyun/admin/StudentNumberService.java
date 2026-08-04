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
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^(20\\d{2})(\\d{4})(\\d{2})(\\d{2,4})$");
    private final JdbcTemplate jdbc;

    public StudentNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> infer(String studentNo) {
        Map<String, String> result = new LinkedHashMap<>();
        if (studentNo == null || studentNo.isBlank()) return result;
        Matcher matcher = NUMBER_PATTERN.matcher(studentNo.trim());
        if (!matcher.matches()) return result;
        String gradeYear = matcher.group(1);
        String classCode = matcher.group(3);
        result.put("gradeYear", gradeYear);
        List<Map<String, Object>> matches = jdbc.query("""
                select name, grade_year
                from administrative_class
                where grade_year=?
                order by case when name like ? then 0 else 1 end, id
                """, (rs, row) -> Map.of("name", rs.getString("name"), "gradeYear", rs.getString("grade_year")),
                gradeYear, "%" + classCode + "班%");
        if (!matches.isEmpty()) {
            result.put("administrativeClassName", String.valueOf(matches.get(0).get("name")));
        }
        return result;
    }
}
