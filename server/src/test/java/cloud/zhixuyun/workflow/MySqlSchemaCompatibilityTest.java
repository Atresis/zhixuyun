package cloud.zhixuyun.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSchemaCompatibilityTest {
    @Test
    void mysqlSchemaCreatesRestoredWorkflowColumns() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:mysql-schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("schema-mysql.sql"), StandardCharsets.UTF_8));
            try (var columns = connection.getMetaData().getColumns(null, null, "task_submission", "review_status")) {
                assertTrue(columns.next());
            }
            try (var tables = connection.getMetaData().getTables(null, null, "submission_version", new String[]{"TABLE"})) {
                assertTrue(tables.next());
            }
        }
    }
}
