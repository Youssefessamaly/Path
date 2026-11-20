package com.internplatform.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only implementation: never included in the production jar. */
@Component
@Profile("test")
public class InMemoryObjectStorage implements ObjectStorage {
    private final Map<String, byte[]> data = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        data.put(key, Arrays.copyOf(bytes, bytes.length));
    }

    @Override
    public byte[] get(String key) {
        byte[] bytes = data.get(key);
        if (bytes == null) throw NoSuchKeyException.builder().message("Not found").statusCode(404).build();
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public void deletePrefix(String prefix) {
        data.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public boolean contains(String key) { return data.containsKey(key); }
}
