package com.meet5.interactionservice.repository;

import com.meet5.interactionservice.dto.VisitorSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class VisitRepository {
    JdbcClient jdbcClient;

    public VisitRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
    @Transactional
    public Integer recordVisits(UUID visitorId, UUID visitedId) {
        Instant now = Instant.now();
        return jdbcClient.sql("""
                        INSERT INTO profile_visits(id, visitor_id, visited_id, visit_count, first_visited_at, last_visited_at)
                        VALUES(:id, :visitorId, :visitedId, 1, :visitAt, :visitAt)
                        ON CONFLICT (visitor_id, visited_id)
                        DO UPDATE SET
                           visit_count = profile_visits.visit_count + 1,
                           last_visited_at = NOW()
                           RETURNING visit_count
                        """)
                .param("id", UUID.randomUUID())
                .param("visitorId", visitorId)
                .param("visitedId", visitedId)
                .param("visitAt", Timestamp.from(now))
                .query(Integer.class)
                .single();
    }

    @Transactional(readOnly = true)
    public List<VisitorSummary> findVisitors(UUID userId, int limit, int offset) {
        List<VisitorSummary> visitors = new ArrayList<>();
            return jdbcClient.sql("""
                    SELECT visitor_id, visit_count, first_visited_at, last_visited_at FROM profile_visits
                    WHERE visited_id = :userId
                    ORDER BY last_visited_at DESC
                    LIMIT :limit OFFSET :offset
                    """)
                    .param("userId", userId)
                    .param("limit", limit)
                    .param("offset", offset)
                    .query((rs, rowNum) ->
                       new VisitorSummary(UUID.fromString(rs.getString("visitor_id")),
                                    rs.getInt("visit_count"),
                                    rs.getTimestamp("first_visited_at").toInstant(),
                                    rs.getTimestamp("last_visited_at").toInstant())

                    ).list();
    }
}
