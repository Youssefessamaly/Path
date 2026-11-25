package com.internplatform.support;

/** How the visitor identifies themselves on the public support form — a triage hint only,
 *  never checked against any real account/role, since the submitter may not even have one
 *  (e.g. someone who can't log in at all) or may not want to identify which account is theirs. */
public enum SupportAccountType {
    INTERN,
    COMPANY,
    OTHER
}
