package com.project.explosive.util;

import java.util.function.Supplier;

public record RegistrySupplier<T>(T value) implements Supplier<T> {
    @Override
    public T get() {
        return value;
    }
}
