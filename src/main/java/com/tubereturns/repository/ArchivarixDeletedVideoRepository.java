package com.tubereturns.repository;

import com.tubereturns.model.ArchivarixDeletedVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ArchivarixDeletedVideoRepository extends JpaRepository<ArchivarixDeletedVideo, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO archivarix_deleted_videos
                (channel_id, youtube_video_id, title, upload_date, archivarix_status, discovered_at)
            VALUES
                (:channelId, :videoId, :title, CAST(:uploadDate AS DATE), :status, NOW())
            ON CONFLICT (channel_id, youtube_video_id) DO UPDATE
                SET title = EXCLUDED.title,
                    upload_date = EXCLUDED.upload_date,
                    archivarix_status = EXCLUDED.archivarix_status
            """, nativeQuery = true)
    void upsert(@Param("channelId") Long channelId,
                @Param("videoId") String videoId,
                @Param("title") String title,
                @Param("uploadDate") String uploadDate,
                @Param("status") String status);
}
