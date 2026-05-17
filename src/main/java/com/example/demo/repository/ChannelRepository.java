package com.example.demo.repository;

import com.example.demo.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findBySlug(String slug);

    List<Channel> findByProjectId(Long projectId);

    Optional<Channel> findBySlugAndProjectId(String slug, Long projectId);
}
