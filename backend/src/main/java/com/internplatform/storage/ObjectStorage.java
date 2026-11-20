package com.internplatform.storage;

/** A private bucket interface. Only backend-authenticated controllers expose stored bytes. */
public interface ObjectStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
    /** Idempotent for missing objects; throws if R2 is unavailable. */
    void delete(String key);
    /** Deletes every object under the given *complete*, slash-terminated prefix. */
    void deletePrefix(String prefix);
}
