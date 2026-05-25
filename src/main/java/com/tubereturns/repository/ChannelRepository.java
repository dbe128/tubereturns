package com.tubereturns.repository;

import com.tubereturns.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
