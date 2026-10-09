package com.stomatologia.client.ui;

import com.stomatologia.client.model.NotificationModels.NotificationDto;
import com.stomatologia.client.model.NotificationModels.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class NotifierTest {

    private static NotificationDto lead(long id) {
        return new NotificationDto(NotificationType.NEW_LEAD, id, "Новая заявка · Сайт", "Гость " + id, null);
    }

    @Test
    void fewEventsAreShownAsIs() {
        List<NotificationDto> items = List.of(lead(1), lead(2), lead(3));

        assertThat(Notifier.collapse(items)).isEqualTo(items);
    }

    @Test
    void manyEventsCollapseIntoSummaryAndLatestCards() {
        List<NotificationDto> items = LongStream.rangeClosed(1, 7).mapToObj(NotifierTest::lead).toList();

        List<NotificationDto> shown = Notifier.collapse(items);

        assertThat(shown).hasSize(Notifier.MAX_CARDS);
        assertThat(shown.get(0).leadId()).isNull();
        assertThat(shown.get(0).title()).isEqualTo("Ещё 5 событий");
        assertThat(shown.subList(1, 3)).extracting(NotificationDto::leadId).containsExactly(6L, 7L);
    }

    @Test
    void chimeIsShortAndNotTooLoud() {
        byte[] sound = Chime.tone();
        int peak = 0;
        for (int i = 0; i < sound.length; i += 2) {
            peak = Math.max(peak, Math.abs((short) ((sound[i] & 0xff) | (sound[i + 1] << 8))));
        }

        assertThat(sound.length / 2 / Chime.RATE).isBetween(0.2f, 0.5f);
        assertThat(peak).isBetween(Short.MAX_VALUE / 5, (int) (Short.MAX_VALUE * 0.36));
    }
}
