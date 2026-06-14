package org.lifuscator.core.registry;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class Registry<T> {

    @Getter
    private final List<T> entries = new ArrayList<>();

    protected Registry() {
        register();
    }

    protected abstract void register();

    protected final void add(T entry) {
        entries.add(entry);
    }
}
