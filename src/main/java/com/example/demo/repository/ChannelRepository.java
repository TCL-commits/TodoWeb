package com.example.demo.repository;

import com.example.demo.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findBySlug(String slug);
}
