package io.github.opensabre.iac.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import io.github.opensabre.iac.consumer.ConsumerApplicationMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Opt-in migration/import verification, guarded by a disposable local server's port and datadir. */
class IacMigrationMysqlIT {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:34309/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    @Test void freshBaselineAndHistoricalImportPreserveAllSevenTables() throws Exception {
        String directory = verifyIsolatedDatabase();
        String sourceMigrations = System.getenv("IAC_SOURCE_MIGRATIONS_DIR");
        assertThat(sourceMigrations).startsWith("/private/tmp/iqc-iac-split/");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String source = "iqc_import_" + suffix, target = "iac_import_" + suffix;
        try (var connection = DriverManager.getConnection(SERVER + OPTIONS,"root",""); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + source + " CHARACTER SET utf8mb4");
            statement.execute("CREATE DATABASE " + target + " CHARACTER SET utf8mb4");
        }
        var original = Flyway.configure().dataSource(SERVER+source+OPTIONS,"root","")
                .locations("filesystem:"+sourceMigrations).load();
        original.migrate(); original.validate();
        var destination = Flyway.configure().dataSource(SERVER+target+OPTIONS,"root","")
                .locations("classpath:db/migration/mysql").load();
        assertThat(destination.migrate().migrationsExecuted).isEqualTo(5);
        destination.validate(); assertThat(destination.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(SERVER+source+OPTIONS,"root",""); var statement = connection.createStatement()) {
            // Include text resembling source table names to catch unsafe global SQL replacements.
            statement.execute("INSERT INTO iqc_quality_agent(id,name,code,status,config_json,version_no,created_by) VALUES ('agent-a','中文😀','a','PUBLISHED','{\"schemaVersion\":\"3.0\",\"systemPrompt\":\"iqc_quality_agent\"}',3,'alice')");
            statement.execute("INSERT INTO iqc_quality_agent_version(id,agent_id,version_no,name,code,status,config_json) VALUES ('version-a','agent-a',3,'中文😀','a','PUBLISHED','{\"prompt\":\"iqc_quality_agent_version\"}')");
            statement.execute("INSERT INTO iqc_model_profile(id,name,code,provider,model_name,status,secret_ref) VALUES ('model-a','model','m','OPENAI','test','ENABLED','ENC(isolated-reference)')");
            statement.execute("INSERT INTO iqc_mcp_server(id,name,code,transport,endpoint,auth_type,status) VALUES ('mcp-a','MCP','mc','SSE','http://127.0.0.1:9','NONE','ENABLED')");
            statement.execute("INSERT INTO iqc_skill(id,name,code,instructions,status) VALUES ('skill-a','Skill','s','iqc_skill 😀','ENABLED')");
            statement.execute("INSERT INTO iqc_skill_version(id,skill_id,version_no,name,code,instructions,status) VALUES ('skill-version-a','skill-a',2,'Skill','s','iqc_skill_version 😀','PUBLISHED')");
            try (var insert = connection.prepareStatement("INSERT INTO iqc_agent_chat_session(id,agent_id,version_no,title,source,owner,owner_group_id,config_hash,message_count,messages_json,last_message_time,created_time,updated_time) VALUES ('session-a','agent-a',3,'中文😀','TEST','alice','g1',REPEAT('a',64),2,?,NOW(3),NOW(3),NOW(3))")) {
                insert.setString(1,"[{\"role\":\"user\",\"content\":\""+"iqc_agent_chat_session 中文😀".repeat(5000)+"\"}]");
                insert.executeUpdate();
            }
        }
        Path credentials = Path.of(directory,"client.cnf");
        Files.writeString(credentials,"[client]\nuser=root\nhost=127.0.0.1\nport=34309\nprotocol=tcp\n");
        Files.setPosixFilePermissions(credentials,PosixFilePermissions.fromString("rw-------"));
        Path report = Path.of(directory,"import-report.json");
        var command = new ArrayList<>(List.of("python3","scripts/import-iqc-assets.py",
                "--source-defaults-file",credentials.toString(),"--target-defaults-file",credentials.toString(),
                "--source-database",source,"--target-database",target,"--mysql",System.getenv("IAC_MYSQL_CLIENT"),
                "--mysqldump",System.getenv("IAC_MYSQL_DUMP"),"--report",report.toString()));
        var noConfirmation = new ProcessBuilder(command).redirectErrorStream(true).start();
        String refusal = new String(noConfirmation.getInputStream().readAllBytes());
        assertThat(noConfirmation.waitFor()).isEqualTo(1); assertThat(refusal).contains("Stop source");
        command.add("--confirm-source-writes-stopped");
        try (var connection = DriverManager.getConnection(SERVER+source+OPTIONS,"root",""); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE iqc_model_profile ADD COLUMN pricing_json TEXT, ADD COLUMN max_output_tokens INT, ADD COLUMN output_token_parameter VARCHAR(32), ADD COLUMN context_window_tokens INT, ADD COLUMN deployment_ref VARCHAR(128)");
            statement.execute("UPDATE iqc_model_profile SET max_output_tokens = 100 WHERE id = 'model-a'");
        }
        var unsupported = new ProcessBuilder(command).redirectErrorStream(true).start();
        String unsupportedOutput = new String(unsupported.getInputStream().readAllBytes());
        assertThat(unsupported.waitFor()).isEqualTo(1);
        assertThat(unsupportedOutput).contains("compatibility fields");
        try (var connection = DriverManager.getConnection(SERVER+source+OPTIONS,"root",""); var statement = connection.createStatement()) {
            statement.execute("UPDATE iqc_model_profile SET max_output_tokens = NULL WHERE id = 'model-a'");
        }
        var imported = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(imported.getInputStream().readAllBytes());
        assertThat(imported.waitFor()).as(output).isZero();
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(report));
        assertThat(proof.path("verified").asBoolean()).isTrue();
        assertThat(proof.path("tables").size()).isEqualTo(7);
        proof.path("tables").forEach(table -> assertThat(table.path("rows").asInt()).isEqualTo(1));
        try (var connection = DriverManager.getConnection(SERVER + target + OPTIONS, "root", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT caller FROM iac_agent_chat_session WHERE id = 'session-a'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("alice");
        }
        var repeated = new ProcessBuilder(command).redirectErrorStream(true).start();
        String repeatedOutput = new String(repeated.getInputStream().readAllBytes());
        assertThat(repeated.waitFor()).isEqualTo(1); assertThat(repeatedOutput).contains("must all be empty");
        destination.validate(); assertThat(destination.migrate().migrationsExecuted).isZero();
    }
    @Test
    void upgradesDeployedHistoryAndEnforcesBothQuotaWindows() throws Exception {
        verifyIsolatedDatabase();
        String database = "iac_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = SERVER + database + OPTIONS;
        try (var connection = DriverManager.getConnection(SERVER + OPTIONS, "root", "");
             var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        }
        var old = Flyway.configure().dataSource(url, "root", "").locations("classpath:db/migration/mysql")
                .target("1.0.1").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(2);
        try (var connection = DriverManager.getConnection(url, "root", ""); var sql = connection.createStatement()) {
            sql.execute("""
                    INSERT INTO iac_agent_chat_session
                      (id, agent_id, version_no, title, source, owner, config_hash, message_count, messages_json,
                       last_message_time, created_time, updated_time)
                    VALUES ('old', 'agent', 1, 'original', 'TEST', 'alice', 'hash', 0, '[]', NOW(), NOW(), NOW())
                    """);
        }
        var destination = Flyway.configure().dataSource(url, "root", "").locations("classpath:db/migration/mysql").load();
        assertThat(destination.migrate().migrationsExecuted).isEqualTo(3);
        destination.validate();
        assertThat(destination.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, "root", ""); var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT caller, title FROM iac_agent_chat_session WHERE id = 'old'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("alice");
            assertThat(rows.getString(2)).isEqualTo("original");
            sql.execute("""
                    INSERT INTO iac_consumer_application
                      (client_id, display_name, enabled, per_minute, per_day, grants_json)
                    VALUES ('app', 'App', TRUE, 1, 2, '[]')
                    """);
        }
        var datasource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "");
        var config = new MybatisConfiguration(new Environment("isolated", new JdbcTransactionFactory(), datasource));
        config.addMapper(ConsumerApplicationMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            var quota = session.getMapper(ConsumerApplicationMapper.class);
            assertThat(quota.reserve("app", "[]", 10, 0)).isOne();
            assertThat(quota.reserve("app", "[]", 10, 0)).isZero();
            assertThat(quota.reserve("app", "[]", 11, 0)).isOne();
            assertThat(quota.reserve("app", "[]", 12, 0)).isZero();
            assertThat(quota.reserve("app", "[]", 1440, 1)).isOne();
            assertThat(quota.reserve("app", "not-current", 1441, 1)).isZero();
        }
    }

    private static String verifyIsolatedDatabase() throws Exception {
        String directory = System.getenv("IAC_MYSQL_VALIDATION_DIR");
        assertThat(directory).startsWith("/private/tmp/iac-mysql-validation.");
        try (var connection = DriverManager.getConnection(SERVER + OPTIONS, "root", "");
             var statement = connection.createStatement(); var row = statement.executeQuery("SELECT @@port,@@datadir")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getInt(1)).isEqualTo(34309);
            assertThat(row.getString(2)).isEqualTo(directory + "/data/");
        }
        return directory;
    }
}
