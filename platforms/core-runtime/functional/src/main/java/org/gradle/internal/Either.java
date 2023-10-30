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

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents values with two possibilities.
 *
 * @param <L> the left type.
 * @param <R> the right type.
 */
public abstract class Either<L, R> {

    public static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    /**
     * Take the value if this is a left.
     */
    public abstract Optional<L> getLeft();

    /**
     * Take the value if this is a right.
     */
    public abstract Optional<R> getRight();

    /**
     * Map the left side.
     *
     * @see #mapRight
     * @see #fold
     */
    public abstract <U, V> Either<U, V> mapLeft(Function<? super L, ? extends U> f);

    /**
     * Map the right side.
     *
     * @see #mapLeft
     * @see #fold
     */
    public abstract <U, V> Either<U, V> mapRight(Function<? super R, ? extends V> f);

    /**
     * Apply the respective function and return its result.
     */
    public abstract <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r);

    /**
     * Apply the respective consumer.
     */
    public abstract void apply(Consumer<? super L> l, Consumer<? super R> r);

    @Override
    public abstract boolean equals(@Nullable Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    private static class Left<L, R> extends Either<L, R> {
        private final L value;

        public Left(L value) {
            this.value = value;
        }

        @Override
        public Optional<L> getLeft() {
            return Optional.of(value);
        }

        @Override
        public Optional<R> getRight() {
            return Optional.empty();
        }

        @Override
        public <U, V> Either<U, V> mapLeft(Function<? super L, ? extends U> f) {
            return new Left<>(f.apply(value));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U, V> Either<U, V> mapRight(Function<? super R, ? extends V> f) {
            return (Either<U, V>) this;
        }

        @Override
        public <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r) {
            return l.apply(value);
        }

        @Override
        public void apply(Consumer<? super L> l, Consumer<? super R> r) {
            l.accept(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return value.equals(((Left<?, ?>) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Left(" + value + ")";
        }
    }

    private static class Right<L, R> extends Either<L, R> {
        private final R value;

        public Right(R value) {
            this.value = value;
        }

        @Override
        public Optional<L> getLeft() {
            return Optional.empty();
        }

        @Override
        public Optional<R> getRight() {
            return Optional.of(value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U, V> Either<U, V> mapLeft(Function<? super L, ? extends U> f) {
            return (Either<U, V>) this;
        }

        @Override
        public <U, V> Either<U, V> mapRight(Function<? super R, ? extends V> f) {
            return new Right<>(f.apply(value));
        }

        @Override
        public <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r) {
            return r.apply(value);
        }

        @Override
        public void apply(Consumer<? super L> l, Consumer<? super R> r) {
            r.accept(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return value.equals(((Right<?, ?>) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Right(" + value + ")";
        }
    }
}
