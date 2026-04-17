package com.caio.escapekind.controller;

import com.caio.escapekind.dto.EventRequestDTO;
import com.caio.escapekind.dto.EventResponseDTO;
import com.caio.escapekind.service.GameEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    private final GameEventService eventService;

    public EventController(GameEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponseDTO> registerEvent(@RequestBody EventRequestDTO request) {
        EventResponseDTO response = eventService.processAndSaveEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}