package com.stomatologia.backend.web;

import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.dto.InvoiceDtos.InvoiceDto;
import com.stomatologia.backend.dto.InvoiceDtos.PaymentRequest;
import com.stomatologia.backend.service.InvoiceService;
import com.stomatologia.backend.service.InvoiceService.Filter;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR', 'PATIENT')")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public List<InvoiceDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Long patientId) {
        return invoiceService.search(new Filter(from, to, status, patientId));
    }

    @GetMapping("/{id}")
    public InvoiceDto get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PostMapping("/for-appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR')")
    public InvoiceDto issueForAppointment(@PathVariable Long appointmentId) {
        return invoiceService.issueForAppointment(appointmentId);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR')")
    public InvoiceDto pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return invoiceService.pay(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceDto cancel(@PathVariable Long id) {
        return invoiceService.cancel(id);
    }
}
