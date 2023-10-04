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

package org.gradle.internal;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Backport of Java 11 methods to Java 8's {@link Optional}.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ExtendedOptional<T> {
    private final Optional<T> delegate;

    private ExtendedOptional(Optional<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> ExtendedOptional<T> extend(Optional<T> delegate) {
        return new ExtendedOptional<>(delegate);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public T get() {
        return delegate.get();
    }

    public boolean isPresent() {
        return delegate.isPresent();
    }

    public boolean isEmpty() {
        return !delegate.isPresent();
    }

    public void ifPresent(Consumer<? super T> action) {
        delegate.ifPresent(action);
    }

    @SuppressWarnings("unused")
    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        T value = delegate.orElse(null);
        if (value != null) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    public Optional<T> filter(Predicate<? super T> predicate) {
        return delegate.filter(predicate);
    }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return delegate.map(mapper);
    }

    @SuppressWarnings({"RedundantCast", "unchecked"})
    public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper) {
        return delegate.flatMap((Function<T, Optional<U>>) mapper);
    }

    public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (isPresent()) {
            return delegate;
        } else {
            @SuppressWarnings("unchecked")
            Optional<T> r = (Optional<T>) supplier.get();
            return Objects.requireNonNull(r);
        }
    }

    public Stream<T> stream() {
        T value = delegate.orElse(null);
        if (value != null) {
            return Stream.of(value);
        } else {
            return Stream.empty();
        }
    }

    public T orElse(T other) {
        return delegate.orElse(other);
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        return delegate.orElseGet(supplier);
    }

    @SuppressWarnings("unused")
    public T orElseThrow() {
        T value = delegate.orElse(null);
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    @SuppressWarnings("unused")
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return delegate.orElseThrow(exceptionSupplier);
    }
}
