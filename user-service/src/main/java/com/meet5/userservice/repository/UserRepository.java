package com.meet5.userservice.repository;

import org.postgresql.PGConnection;
import com.meet5.userservice.domain.User;
import com.meet5.userservice.domain.UserStatus;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static com.meet5.userservice.util.CommonUtil.fromJson;
import static com.meet5.userservice.util.CommonUtil.toJson;

@Repository
public class UserRepository {

    //private final JdbcClient jdbcClient;
    private static final int COPY_THRESHOLD = 500;

    private final NamedParameterJdbcTemplate namedJdbc;
    private final DataSource dataSource;

    public UserRepository(NamedParameterJdbcTemplate namedJdbc, DataSource dataSource) {
        this.namedJdbc = namedJdbc;
        this.dataSource = dataSource;
    }

    @Transactional
    public User insert(User user) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        String sql = """
                INSERT INTO users
                (id, name, username, age, status, extra_fields, created_at, updated_at)
                VALUES
                (:id, :name, :username, :age, :status, CAST(:extraFields AS jsonb), :createdAt, :updatedAt)
                ON CONFLICT (username) DO NOTHING
                """;
        namedJdbc.update(sql, buildParams(userId, user, now));

        return user.builder()
                .id(userId)
                .name(user.getName())
                .username(user.getUsername())
                .age(user.getAge())
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public Optional<User> findById(UUID id) {
        String sql = """
                SELECT id, name, username, age, extra_fields, status, created_at, updated_at
                FROM users
                WHERE id = :id
                """;
                List<User> results = namedJdbc.query(sql, Map.of("id", id), mapToUser());
                return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<User> findByUsername(String username) {
        String sql = """
                SELECT id, name, username, age, extra_fields, status, created_at, updated_at
                FROM users
                WHERE username = :username
                """;

        List<User> results = namedJdbc.query(sql, Map.of("username", username), mapToUser());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Transactional
    public void updateStatus(UUID userId, UserStatus status) {
        String sql = """
                UPDATE users SET status = :status,
                updated_at = :now
                where id = :id
                """;
        namedJdbc.update(sql, Map.of("status", status.toString(),
                "now", Timestamp.from(Instant.now()),
                "id", userId));
    }

    @Transactional
    public int bulkInsert(List<User> users) {
        if (users == null || users.isEmpty()) return 0;

        if(users.size() <= COPY_THRESHOLD) {
            return bulkInsertUsingJdbc(users);
        } else {
            return bulkInsertUsingCopy(users);
        }
    }

    private int bulkInsertUsingJdbc(List<User> users) {
        if (users == null || users.isEmpty()) return 0;

        String sql = """
                INSERT INTO users
                (id, name, username, age, status, extra_fields, created_at, updated_at)
                VALUES
                (:id, :name, :username, :age, :status, CAST(:extraFields AS jsonb), :createdAt, :updatedAt)
                ON CONFLICT (username) DO NOTHING
                """;
        Instant now = Instant.now();

        SqlParameterSource[] batch = users.stream()
                .map(u -> buildParams(UUID.randomUUID(), u, now))
                .toArray(SqlParameterSource[]::new);
        int [] result = namedJdbc.batchUpdate(sql, batch);
        return Arrays.stream(result).sum();
    }

    private int bulkInsertUsingCopy(List<User> users) {
        if (users == null || users.isEmpty()) return 0;
        Instant now = Instant.now();
        String nowStr = now.toString()
                .replace("T", " ")
                .replace("Z", "+00");

        StringBuilder csv = new StringBuilder();
        for (User u : users) {
            csv.append(UUID.randomUUID()).append(',')
                    .append(escapeCsv(u.getName())).append(',')
                    .append(escapeCsv(u.getUsername())).append(',')
                    .append(u.getAge()).append(',')
                    .append(escapeCsv(toJson(u.getExtraFields()))).append(',')
                    .append(UserStatus.ACTIVE.name()).append(',')
                    .append(escapeCsv(nowStr)).append(',')
                    .append(escapeCsv(nowStr))
                    .append('\n');
        }

        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TEMP TABLE IF NOT EXISTS users_staging (
                            id           TEXT,
                            name         TEXT,
                            username     TEXT,
                            age          TEXT,
                            extra_fields TEXT,
                            status       TEXT,
                            created_at   TEXT,
                            updated_at   TEXT
                        ) ON COMMIT DELETE ROWS
                        """);
                stmt.execute("TRUNCATE users_staging");
            }

            PGConnection pgConn = conn.unwrap(PGConnection.class);
            CopyManager copyManager = pgConn.getCopyAPI();

            copyManager.copyIn("""
                    COPY users_staging
                    (id, name, username, age, extra_fields, status, created_at, updated_at)
                    FROM STDIN WITH (FORMAT csv, DELIMITER ',', NULL '')
                    """, new StringReader(csv.toString()));

            try (Statement stmt = conn.createStatement()) {
                return stmt.executeUpdate("""
                        INSERT INTO users
                                            (id, name, username, age, extra_fields,
                                             status, created_at, updated_at)
                                        SELECT
                                            id::uuid,
                                            name,
                                            username,
                                            age::smallint,
                                            extra_fields::jsonb,
                                            status,
                                            created_at::timestamptz,
                                            updated_at::timestamptz
                                        FROM users_staging
                                        ON CONFLICT (username) DO NOTHING
                        """);
            }
        } catch (Exception e) {
            throw new RuntimeException("COPY bulk insert failed", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public RowMapper<User> mapToUser() {
        return (ResultSet rs, int rowNum) -> User.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .username(rs.getString("username"))
                .age(rs.getInt("age"))
                .extraFields(fromJson(rs.getString("extra_fields")))
                .status(UserStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    private MapSqlParameterSource buildParams(UUID id, User user, Instant now) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", user.getName())
                .addValue("username", user.getUsername())
                .addValue("age", user.getAge())
                .addValue("status", UserStatus.ACTIVE.name())
                .addValue("extraFields", toJson(user.getExtraFields()))
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
