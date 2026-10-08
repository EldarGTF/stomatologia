package com.stomatologia.client.model;

public record UserInfo(Long id, String username, String fullName, Role role, Long doctorId, Long patientId) {
}
