package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.RoomDtos.RoomDto;
import com.stomatologia.backend.repository.RoomRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository rooms;

    public RoomController(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public List<RoomDto> findAll() {
        return rooms.findAll(Sort.by("number")).stream().map(RoomDto::from).toList();
    }
}
