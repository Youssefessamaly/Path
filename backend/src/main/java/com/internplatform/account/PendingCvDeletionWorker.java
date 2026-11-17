package com.internplatform.account;

import com.internplatform.internprofile.CvStorageService;
import com.internplatform.profilephoto.ProfilePhotoStorageService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * File operations are NOT transactional. The durable SQL queue survives restarts and
 * failures, so retries cannot result in silently forgotten CV copies.
 */
@Component
@EnableScheduling
public class PendingCvDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(PendingCvDeletionWorker.class);

    private final JdbcTemplate jdbc;
    private final CvStorageService storage;
    private final ProfilePhotoStorageService photos;
    private final TransactionTemplate newTransaction;

    public PendingCvDeletionWorker(JdbcTemplate jdbc, CvStorageService storage,
                                   ProfilePhotoStorageService photos, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.photos = photos;
        this.newTransaction = new TransactionTemplate(transactionManager);
        // AFTER_COMMIT still has the just-committed resources bound to the thread.
        // Queue operations therefore need their own REAL committed transaction.
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeletionCommitted(AccountDeletedEvent ignored) {
        // A cleanup-system outage must NOT turn a successfully committed account deletion
        // into an HTTP 500. The durable queue is retried by @Scheduled after recovery.
        try {
            processPending();
        } catch (Exception failure) {
            log.warn("CV deletion cleanup could not run; queued tasks will be retried");
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void processPending() {
        List<String> tasks = newTransaction.execute(status -> jdbc.queryForList(
                "SELECT task_key FROM pending_cv_deletions ORDER BY created_at LIMIT 200", String.class));
        if (tasks == null) {
            return;
        }
        for (String task : tasks) {
            try {
                if (task.startsWith("photo-profile:")) {
                    photos.deleteProfileDirectory(UUID.fromString(task.substring("photo-profile:".length())));
                } else {
                    storage.deleteAccountCleanupTask(task);
                }
                newTransaction.executeWithoutResult(status ->
                        jdbc.update("DELETE FROM pending_cv_deletions WHERE task_key = ?", task));
            } catch (Exception error) {
                // Leave the task in the DB for the next scheduled attempt; never log CV data.
                log.warn("A pending CV deletion failed and will be retried (task type: {})",
                        task.startsWith("photo-profile:") ? "photo-profile" : task.startsWith("profile:") ? "profile" : "file");
            }
        }
    }
}
