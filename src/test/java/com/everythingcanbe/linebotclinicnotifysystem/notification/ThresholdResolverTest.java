package com.everythingcanbe.linebotclinicnotifysystem.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ThresholdResolverTest {

    @Test
    void normalizeSortsDescendingRemovesDuplicatesAndAddsArrival() {
        assertThat(ThresholdResolver.normalize(List.of(3, 10, 3))).containsExactly(10, 3, 0);
        assertThat(ThresholdResolver.normalize(List.of(10, 3, 0))).containsExactly(10, 3, 0);
    }

}
