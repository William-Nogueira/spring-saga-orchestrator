package williamnogueira.dev.orchestrator.infra.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static java.util.Objects.isNull;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransactionUtils {

    /**
     * Runs an action immediately after the current transaction commits.
     * A failing action triggers the fallback.
     * Both run post-commit, outside the transaction.
     */
    public static void executeAfterCommit(Runnable action, Runnable fallbackAction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("cannot register a post-commit action without an active transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("post-commit action failed, starting fallback strategy: {}", e.getMessage(), e);
                    executeFallback(fallbackAction);
                }
            }
        });
    }

    public static void executeAfterCommit(Runnable action) {
        executeAfterCommit(action, null);
    }

    private static void executeFallback(Runnable fallbackAction) {
        if (isNull(fallbackAction)) {
            return;
        }

        try {
            fallbackAction.run();
        } catch (Exception fallbackException) {
            log.error("CRITICAL: fallback action failed: {}", fallbackException.getMessage(), fallbackException);
        }
    }
}
