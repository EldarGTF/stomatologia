package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.NotificationDtos.NotificationFeed;
import com.stomatologia.backend.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Всплывающие уведомления CRM: клиент опрашивает раз в 15 секунд, передавая последние виденные id.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR')")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationFeed feed(@RequestParam(required = false) Long afterLead,
                                 @RequestParam(required = false) Long afterMessage) {
        return notifications.feed(afterLead, afterMessage);
    }
}
