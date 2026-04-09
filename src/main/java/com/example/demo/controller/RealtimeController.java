package com.example.demo.controller;

import com.example.demo.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private final RealtimeEventService realtimeEventService;

    @GetMapping("/events")
    public SseEmitter subscribe() {
        return realtimeEventService.subscribe();
    }
}
