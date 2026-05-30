package com.tubereturns.repository;

import com.tubereturns.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByHandle(String handle);

    Optional<Channel> findByNameSlug(String nameSlug);

    @Query("SELECT c FROM Channel c WHERE LOWER(c.handle) = LOWER(:slug) OR LOWER(REPLACE(c.handle, '_', '-')) = LOWER(:slug)")
    Optional<Channel> findBySlugOrHandle(@Param("slug") String slug);

    @Query(value = "SELECT * FROM channels WHERE handle = :handle", nativeQuery = true)
    Optional<Channel> findByHandleIncludingDeleted(@Param("handle") String handle);

    boolean existsByHandle(String handle);

    @Query(value = """
            SELECT * FROM channels
            WHERE deleted_at IS NULL
              AND archivarix_checked_at IS NULL
              AND handle NOT LIKE 'mock-%'
            ORDER BY subscriber_count DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<Channel> findUnsyncedArchivarixChannels(@Param("limit") int limit);

    @Query(value = "SELECT * FROM channels WHERE youtube_channel_id IS NULL AND handle NOT LIKE 'mock-%'", nativeQuery = true)
    List<Channel> findChannelsWithoutYoutubeChannelId();

    @Modifying
    @Transactional
    @Query(value = "UPDATE channels SET youtube_channel_id = :youtubeChannelId WHERE id = :id", nativeQuery = true)
    void updateYoutubeChannelId(@Param("id") Long id, @Param("youtubeChannelId") String youtubeChannelId);
}
