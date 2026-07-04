package williamnogueira.dev.orchestrator.infra.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TransactionUtilsTest {

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("registering a post-commit action outside a transaction is rejected")
    void rejectsRegistrationWithoutActiveTransaction() {
        assertThatIllegalStateException()
                .isThrownBy(() -> TransactionUtils.executeAfterCommit(() -> {
                }))
                .withMessageContaining("active transaction");
    }

    @Test
    @DisplayName("the action runs only once the transaction commits")
    void runsActionOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var ran = new AtomicBoolean();

        TransactionUtils.executeAfterCommit(() -> ran.set(true));
        assertThat(ran).isFalse();

        triggerAfterCommit();
        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("the fallback runs when the action fails")
    void fallbackRunsWhenActionFails() {
        TransactionSynchronizationManager.initSynchronization();
        var fallbackRan = new AtomicBoolean();

        TransactionUtils.executeAfterCommit(
                () -> {
                    throw new IllegalStateException("broker down");
                },
                () -> fallbackRan.set(true));
        triggerAfterCommit();

        assertThat(fallbackRan).isTrue();
    }

    @Test
    @DisplayName("an action failure without a fallback never propagates")
    void actionFailureWithoutFallbackIsSwallowed() {
        TransactionSynchronizationManager.initSynchronization();

        TransactionUtils.executeAfterCommit(() -> {
            throw new IllegalStateException("broker down");
        });

        assertThatCode(this::triggerAfterCommit).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a failing fallback never propagates")
    void fallbackFailureIsSwallowed() {
        TransactionSynchronizationManager.initSynchronization();

        TransactionUtils.executeAfterCommit(
                () -> {
                    throw new IllegalStateException("broker down");
                },
                () -> {
                    throw new IllegalStateException("fallback also down");
                });

        assertThatCode(this::triggerAfterCommit).doesNotThrowAnyException();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
