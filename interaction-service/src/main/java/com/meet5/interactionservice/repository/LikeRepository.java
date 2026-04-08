package com.meet5.interactionservice.repository;

import com.meet5.interactionservice.dto.LikeRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class LikeRepository {

    JdbcClient jdbcClient;

    public LikeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public boolean recordLikes(UUID likerId, UUID likedId) {
        int row = jdbcClient.sql("""
                INSERT INTO profile_likes(id, liker_id, liked_id, liked_at) VALUES
                (:id, :likerId, :likedId, :likedAt)
                """)
                .param("id", UUID.randomUUID())
                .param("likerId", likerId)
                .param("likedId", likedId)
                .param("likedAt", Timestamp.from(Instant.now()))
                .update();
        return row > 0;
    }
}
