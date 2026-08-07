package cloud.zhixuyun.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class BackupDataSourceConfig {
    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(Environment environment) {
        String url = environment.getProperty("spring.datasource.url",
                "jdbc:h2:file:./.local/zhixuyun;MODE=PostgreSQL;AUTO_SERVER=TRUE;DATABASE_TO_LOWER=TRUE");
        String username = environment.getProperty("spring.datasource.username", "sa");
        String password = environment.getProperty("spring.datasource.password", "");
        String driver = environment.getProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();
    }

    @Bean(name = "jdbcTemplate")
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "backupDataSource")
    public DataSource backupDataSource(Environment environment) {
        String url = environment.getProperty("zhixuyun.backup.datasource.url",
                "jdbc:h2:file:./.local/zhixuyun-backup;MODE=PostgreSQL;AUTO_SERVER=TRUE;DATABASE_TO_LOWER=TRUE");
        String username = environment.getProperty("zhixuyun.backup.datasource.username", "sa");
        String password = environment.getProperty("zhixuyun.backup.datasource.password", "");
        String driver = environment.getProperty("zhixuyun.backup.datasource.driver-class-name", "org.h2.Driver");
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();
    }

    @Bean(name = "backupJdbcTemplate")
    public JdbcTemplate backupJdbcTemplate(@Qualifier("backupDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
