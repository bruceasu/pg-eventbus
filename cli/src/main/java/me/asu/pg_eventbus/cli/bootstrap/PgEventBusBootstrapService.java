package me.asu.pg_eventbus.cli.bootstrap;

import me.asu.pg_eventbus.cli.PgEventBusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PgEventBusBootstrapService {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final JdbcTemplate jdbcTemplate;
    private final PgEventBusProperties properties;

    public PgEventBusBootstrapService(JdbcTemplate jdbcTemplate, PgEventBusProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void initialize(String owner) {
        String schema = normalizeIdentifier(properties.schema(), "bus");
        String grantee = normalizeIdentifier(owner, null);
        if (grantee == null) {
            throw new IllegalArgumentException("Database owner is required for bootstrap");
        }
        String sql = readScript("init.sql");
        sql = replaceToken(sql, "bus", schema);
        sql = replaceToken(sql, "app_role", grantee);
        executeSql(sql);
    }

    private void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }

    private String readScript(String scriptName) {
        String classpathLocation = scriptName;
        try (InputStream stream = PgEventBusBootstrapService.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read SQL bootstrap script from classpath: " + classpathLocation, ex);
        }

        Path sqlDir = resolveSqlDirectory();
        Path script = sqlDir.resolve(scriptName);
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Missing SQL bootstrap script: " + script);
        }
        try {
            return Files.readString(script, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read SQL bootstrap script: " + script, ex);
        }
    }

    private Path resolveSqlDirectory() {
        String explicit = System.getenv("PGEVENTBUS_SQL_DIR");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit.trim());
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs").resolve("sql");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate docs/sql bootstrap directory");
    }

    private static String replaceToken(String sql, String token, String replacement) {
        return Pattern.compile("\\b" + Pattern.quote(token) + "\\b")
                .matcher(sql)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String normalizeIdentifier(String value, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value.trim();
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (!VALID_IDENTIFIER.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL identifier: " + candidate);
        }
        return candidate;
    }

}
