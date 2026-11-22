package io.github.opensabre.iac.integration;

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
        String directory = System.getenv("IAC_MYSQL_VALIDATION_DIR");
        assertThat(directory).startsWith("/private/tmp/iac-mysql-validation.");
        String sourceMigrations = System.getenv("IAC_SOURCE_MIGRATIONS_DIR");
        assertThat(sourceMigrations).startsWith("/private/tmp/iqc-iac-split/");
        try (var connection = DriverManager.getConnection(SERVER + OPTIONS,"root","");
             var statement = connection.createStatement(); var row = statement.executeQuery("SELECT @@port,@@datadir")) {
            assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isEqualTo(34309);
            assertThat(row.getString(2)).isEqualTo(directory + "/data/");
        }
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
        assertThat(destination.migrate().migrationsExecuted).isEqualTo(1);
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
        var imported = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(imported.getInputStream().readAllBytes());
        assertThat(imported.waitFor()).as(output).isZero();
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(report));
        assertThat(proof.path("verified").asBoolean()).isTrue();
        assertThat(proof.path("tables").size()).isEqualTo(7);
        proof.path("tables").forEach(table -> assertThat(table.path("rows").asInt()).isEqualTo(1));
        var repeated = new ProcessBuilder(command).redirectErrorStream(true).start();
        String repeatedOutput = new String(repeated.getInputStream().readAllBytes());
        assertThat(repeated.waitFor()).isEqualTo(1); assertThat(repeatedOutput).contains("must all be empty");
        destination.validate(); assertThat(destination.migrate().migrationsExecuted).isZero();
    }
}
