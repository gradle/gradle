/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.io;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tools to work around {@link IOException}s when using functional Java APIs
 * that take {@link Supplier}s, {@link Consumer}s and {@link Function}s.
 *
 * Any {@code IOException}s are rethrown as {@link UncheckedIOException}.
 */
public class IoFunctions {

    /**
     * Wraps an {@link IOException}-throwing {@link IoConsumer} into a regular {@link Consumer}.
     *
     * Any {@code IOException}s are rethrown as {@link UncheckedIOException}.
     */
    public static <T> Consumer<T> wrapConsumer(IoConsumer<T> consumer) {
        return (payload) -> {
            try {
                consumer.accept(payload);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * Wraps an {@link IOException}-throwing {@link IoSupplier} into a regular {@link Supplier}.
     *
     * Any {@code IOException}s are rethrown as {@link UncheckedIOException}.
     */
    public static <T> Supplier<T> wrapSupplier(IoSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * Wraps an {@link IOException}-throwing {@link IoFunction} into a regular {@link Function}.
     *
     * Any {@code IOException}s are rethrown as {@link UncheckedIOException}.
     */
    public static <T, R> Function<T, R> wrapFunction(IoFunction<T, R> function) {
        return (t) -> {
            try {
                return function.apply(t);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * A variant of {@link Consumer} that is allowed to throw {@link IOException}.
     */
    @FunctionalInterface
    public interface IoConsumer<T> {
        void accept(@Nullable T payload) throws IOException;
    }

    /**
     * A variant of {@link Supplier} that is allowed to throw {@link IOException}.
     */
    @FunctionalInterface
    public interface IoSupplier<T> {
        @Nullable T get() throws IOException;
    }

    /**
     * A variant of {@link Function} that is allowed to throw {@link IOException}.
     */
    @FunctionalInterface
    public interface IoFunction<T, R> {
        @Nullable R apply(@Nullable T t) throws IOException;
    }
}
