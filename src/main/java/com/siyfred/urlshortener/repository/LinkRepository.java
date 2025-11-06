package com.siyfred.urlshortener.repository;

import com.siyfred.urlshortener.model.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);

    @Modifying
    @Transactional
    @Query("UPDATE Link l SET l.clickCount = l.clickCount + 1 WHERE l.shortCode = :code")
    void incrementClickCount(@Param("code") String shortCode);
}
