package com.stomatologia.client.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeadDialogsTest {

    @Test
    void twoWordsAreFirstNameThenLastName() {
        assertThat(LeadDialogs.splitName("Дмитрий Ковалёв")).containsExactly("Ковалёв", "Дмитрий");
    }

    @Test
    void threeWordsAreFullRussianName() {
        assertThat(LeadDialogs.splitName("Иванов Пётр Сергеевич")).containsExactly("Иванов", "Пётр");
    }

    @Test
    void singleWordIsFirstName() {
        assertThat(LeadDialogs.splitName("  Айгерим ")).containsExactly("", "Айгерим");
    }

    @Test
    void blankName() {
        assertThat(LeadDialogs.splitName(null)).containsExactly("", "");
    }
}
