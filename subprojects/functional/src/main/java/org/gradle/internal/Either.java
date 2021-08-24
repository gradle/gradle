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
     * Is this a left?
     */
    public abstract boolean isLeft();

    /**
     * Is this a right?
     */
    public abstract boolean isRight();

    /**
     * Take the value if this is a left.
     */
    public abstract Optional<L> ifLeft();

    /**
     * Take the value if this is a right.
     */
    public abstract Optional<R> ifRight();

    /**
     * Map either case.
     */
    public abstract <U, V> Either<U, V> map(Function<? super L, ? extends U> l, Function<? super R, ? extends V> r);

    /**
     * Flat map either case.
     */
    public abstract <U, V> Either<U, V> flatMap(Function<? super L, ? extends Either<? extends U, ? extends V>> l, Function<? super R, ? extends Either<? extends U, ? extends V>> r);

    /**
     * Apply the respective function and return its result.
     */
    public abstract <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r);

    /**
     * Apply the respective consumer.
     */
    public abstract void apply(Consumer<? super L> l, Consumer<? super R> r);

    private static class Left<L, R> extends Either<L, R> {
        private final L value;

        public Left(L value) {
            this.value = value;
        }

        @Override
        public boolean isLeft() {
            return true;
        }

        @Override
        public boolean isRight() {
            return false;
        }

        @Override
        public Optional<L> ifLeft() {
            return Optional.of(value);
        }

        @Override
        public Optional<R> ifRight() {
            return Optional.empty();
        }

        @Override
        public <U, V> Either<U, V> map(Function<? super L, ? extends U> l, Function<? super R, ? extends V> r) {
            return new Left<>(l.apply(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U, V> Either<U, V> flatMap(Function<? super L, ? extends Either<? extends U, ? extends V>> l, Function<? super R, ? extends Either<? extends U, ? extends V>> r) {
            return (Either<U, V>) l.apply(value);
        }

        @Override
        public <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r) {
            return l.apply(value);
        }

        @Override
        public void apply(Consumer<? super L> l, Consumer<? super R> r) {
            l.accept(value);
        }
    }

    private static class Right<L, R> extends Either<L, R> {
        private final R value;

        public Right(R value) {
            this.value = value;
        }

        @Override
        public boolean isLeft() {
            return false;
        }

        @Override
        public boolean isRight() {
            return true;
        }

        @Override
        public Optional<L> ifLeft() {
            return Optional.empty();
        }

        @Override
        public Optional<R> ifRight() {
            return Optional.of(value);
        }

        @Override
        public <U, V> Either<U, V> map(Function<? super L, ? extends U> l, Function<? super R, ? extends V> r) {
            return new Right<>(r.apply(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U, V> Either<U, V> flatMap(Function<? super L, ? extends Either<? extends U, ? extends V>> l, Function<? super R, ? extends Either<? extends U, ? extends V>> r) {
            return (Either<U, V>) r.apply(value);
        }

        @Override
        public <U> U fold(Function<? super L, ? extends U> l, Function<? super R, ? extends U> r) {
            return r.apply(value);
        }

        @Override
        public void apply(Consumer<? super L> l, Consumer<? super R> r) {
            r.accept(value);
        }
    }
}
