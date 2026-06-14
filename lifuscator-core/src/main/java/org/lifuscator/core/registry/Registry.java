package org.lifuscator.core.registry;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Registry<T extends IFeature> {

    @Getter
    private final Map<Class<? extends T>, T> entries = new LinkedHashMap<>();

    protected Registry() {
        register();
    }

    protected abstract void register();

    protected final void add(T entry) {
        Class<? extends T> key = (Class<? extends T>) entry.getClass();
        if (entries.putIfAbsent(key, entry) != null) {
            throw new IllegalStateException("Duplicate entry: " + key.getName());
        }
    }

    public <U extends T> U get(Class<U> type) {
        return (U) entries.get(type);
    }
}
