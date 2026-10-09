package com.stomatologia.backend.domain;

public enum ChatChannel {
    TELEGRAM("Telegram", LeadSource.TELEGRAM),
    WHATSAPP("WhatsApp", LeadSource.WHATSAPP),
    WEB_CHAT("чат на сайте", LeadSource.WEBSITE);

    private final String title;
    private final LeadSource leadSource;

    ChatChannel(String title, LeadSource leadSource) {
        this.title = title;
        this.leadSource = leadSource;
    }

    public String title() {
        return title;
    }

    /** Каким источником помечается заявка из этого канала. */
    public LeadSource leadSource() {
        return leadSource;
    }
}
