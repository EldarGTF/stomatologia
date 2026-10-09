package com.stomatologia.backend.web;

import com.stomatologia.backend.assistant.ConversationService;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.dto.LeadDtos.ChatMessageDto;
import com.stomatologia.backend.dto.LeadDtos.LeadBookRequest;
import com.stomatologia.backend.dto.LeadDtos.LeadDto;
import com.stomatologia.backend.dto.LeadDtos.LeadRequest;
import com.stomatologia.backend.dto.LeadDtos.OperatorMessageRequest;
import com.stomatologia.backend.dto.LeadDtos.RejectRequest;
import com.stomatologia.backend.dto.PatientDtos.PatientDto;
import com.stomatologia.backend.service.LeadService;
import com.stomatologia.backend.service.LeadService.Filter;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Заявки (лиды) — рабочее место администратора и регистратора.
 */
@RestController
@RequestMapping("/api/leads")
@PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR')")
public class LeadController {

    private final LeadService leadService;
    private final ConversationService conversations;

    public LeadController(LeadService leadService, ConversationService conversations) {
        this.leadService = leadService;
        this.conversations = conversations;
    }

    @GetMapping
    public List<LeadDto> search(
            @RequestParam(defaultValue = "false") boolean open,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        return leadService.search(new Filter(open, status, source, from, to, q));
    }

    @GetMapping("/{id}")
    public LeadDto get(@PathVariable Long id) {
        return leadService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadDto create(@Valid @RequestBody LeadRequest request) {
        return leadService.create(request);
    }

    @PutMapping("/{id}")
    public LeadDto update(@PathVariable Long id, @Valid @RequestBody LeadRequest request) {
        return leadService.update(id, request);
    }

    @PostMapping("/{id}/take")
    public LeadDto take(@PathVariable Long id) {
        return leadService.take(id);
    }

    @PostMapping("/{id}/reject")
    public LeadDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return leadService.reject(id, request.reason());
    }

    @PostMapping("/{id}/reopen")
    public LeadDto reopen(@PathVariable Long id) {
        return leadService.reopen(id);
    }

    @PostMapping("/{id}/confirm")
    public LeadDto confirm(@PathVariable Long id) {
        return leadService.confirm(id);
    }

    @PostMapping("/{id}/book")
    public LeadDto book(@PathVariable Long id, @Valid @RequestBody LeadBookRequest request) {
        return leadService.book(id, request);
    }

    @GetMapping("/{id}/patients")
    public List<PatientDto> matchingPatients(@PathVariable Long id) {
        return leadService.matchingPatients(id);
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageDto> messages(@PathVariable Long id) {
        return leadService.messages(id);
    }

    /** Ответ клиенту от имени администратора: дальше разговор ведёт человек. */
    @PostMapping("/{id}/messages")
    public List<ChatMessageDto> reply(@PathVariable Long id, @Valid @RequestBody OperatorMessageRequest request) {
        conversations.operatorReply(id, request.text());
        return leadService.messages(id);
    }

    @PostMapping("/{id}/conversation/assistant")
    public LeadDto returnToAssistant(@PathVariable Long id) {
        conversations.returnToAssistant(id);
        return leadService.get(id);
    }
}
