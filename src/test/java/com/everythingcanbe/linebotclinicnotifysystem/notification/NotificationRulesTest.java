package com.everythingcanbe.linebotclinicnotifysystem.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.notification.rule.MissedNumberRule;
import com.everythingcanbe.linebotclinicnotifysystem.notification.rule.SessionResetRule;
import com.everythingcanbe.linebotclinicnotifysystem.notification.rule.ThresholdReachedRule;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

class NotificationRulesTest {

    private static final List<Integer> THRESHOLDS = List.of(10, 3, 0);

    private final List<NotificationRule> rules = List.of(
            new SessionResetRule(), new MissedNumberRule(), new ThresholdReachedRule());

    private final TrackingJob job = new TrackingJob(
            new Subscriber("U1", null, LocalDateTime.now()), "wuobs", 2, 56, LocalDateTime.now());

    @Test
    void noNotificationBeforeThreshold() {
        assertThat(evaluate(40, 39, Set.of())).isEmpty();
    }

    @Test
    void crossingOneThresholdPushesIt() {
        NotificationDecision decision = evaluate(52, 50, Set.of()).orElseThrow();

        assertThat(decision.type()).isEqualTo(NotificationType.PROGRESS);
        assertThat(decision.pushedThreshold()).isEqualTo(10);
        assertThat(decision.recordThresholds()).containsExactly(10);
        assertThat(decision.endReason()).isNull();
    }

    @Test
    void jumpingOverSeveralThresholdsPushesOnlyTheSmallest() {
        NotificationDecision decision = evaluate(54, 45, Set.of()).orElseThrow();

        assertThat(decision.type()).isEqualTo(NotificationType.PROGRESS);
        assertThat(decision.pushedThreshold()).isEqualTo(3);
        assertThat(decision.recordThresholds()).containsExactlyInAnyOrder(10, 3);
    }

    @Test
    void alreadySentThresholdIsNotRepeated() {
        assertThat(evaluate(52, 51, Set.of(10))).isEmpty();
    }

    @Test
    void arrivalEndsJob() {
        NotificationDecision decision = evaluate(56, 55, Set.of(10, 3)).orElseThrow();

        assertThat(decision.type()).isEqualTo(NotificationType.ARRIVED);
        assertThat(decision.endReason()).isEqualTo(EndReason.ARRIVED);
    }

    @Test
    void skippingPastTargetIsMissed() {
        NotificationDecision decision = evaluate(58, 54, Set.of(10, 3)).orElseThrow();

        assertThat(decision.type()).isEqualTo(NotificationType.MISSED);
        assertThat(decision.endReason()).isEqualTo(EndReason.MISSED);
    }

    @Test
    void largeDropIsSessionReset() {
        NotificationDecision decision = evaluate(2, 40, Set.of()).orElseThrow();

        assertThat(decision.type()).isEqualTo(NotificationType.SESSION_RESET);
        assertThat(decision.endReason()).isEqualTo(EndReason.SESSION_RESET);
    }

    @Test
    void smallDropIsNotSessionReset() {
        // 回頭叫過號病人：號碼小幅倒退，已送出的門檻不重送
        assertThat(evaluate(50, 53, Set.of(10))).isEmpty();
    }

    @Test
    void unknownPreviousNumberStillEvaluatesThresholds() {
        NotificationDecision decision = evaluate(54, null, Set.of(10)).orElseThrow();

        assertThat(decision.pushedThreshold()).isEqualTo(3);
    }

    private Optional<NotificationDecision> evaluate(int current, Integer previous, Set<Integer> sent) {
        RoomStatus status = new RoomStatus("wuobs", 2, "二診", "吳瑞聰", "婦產科", current, true, LocalDateTime.now());
        EvaluationContext context = new EvaluationContext(previous, THRESHOLDS, sent, 10);
        return rules.stream()
                .map(rule -> rule.evaluate(job, status, context))
                .flatMap(Optional::stream)
                .findFirst();
    }

}
