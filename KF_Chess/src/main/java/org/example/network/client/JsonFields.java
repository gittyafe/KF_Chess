package org.example.network.client;

import java.util.List;
import java.util.Map;

/**
 * Tiny casting helpers shared by {@link GameSnapshotMapper} and
 * {@link ServerMessageDispatcher}. Jackson hands back raw {@code Map<String,Object>}
 * trees (no typed DTOs), so both of those classes were full of repeated
 * {@code ((Number) x).intValue()} / {@code ((String) x).charAt(0)} casts.
 * Pulling them out to one place means a future change to how we decode a
 * field (e.g. tolerating a missing value) happens once, not at every call site.
 */
final class JsonFields {

    private JsonFields() {}

    static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    static char charValue(Object value) {
        return ((String) value).charAt(0);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
