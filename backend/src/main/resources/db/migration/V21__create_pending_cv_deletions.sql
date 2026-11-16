-- Durable cleanup outbox for CV files after a successful account-deletion commit.
-- A task is either profile:<UUID> (the intern's entire isolated CV directory) or
-- file:<server-generated storage key> (a snapshot associated with a deleted listing).
-- File deletion must NEVER run inside the SQL transaction: a rollback cannot restore files.
CREATE TABLE pending_cv_deletions (
    task_key VARCHAR(300) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
