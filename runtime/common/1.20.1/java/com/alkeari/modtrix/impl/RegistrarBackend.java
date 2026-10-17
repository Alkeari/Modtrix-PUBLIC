package com.alkeari.modtrix.impl;

import java.util.function.Supplier;

public interface RegistrarBackend<T> {
    <E extends T> Supplier<E> register(String name, Supplier<E> factory);
}
