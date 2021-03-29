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

import java.util.function.Function;

import static org.gradle.internal.Cast.uncheckedCast;

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

    public abstract <U> U fold(Function<L, U> l, Function<R, U> r);

    public abstract <U> Either<L, U> map(Function<R, U> r);

    private static class Left<L, R> extends Either<L, R> {
        private final L value;

        public Left(L value) {
            this.value = value;
        }

        @Override
        public <U> U fold(Function<L, U> l, Function<R, U> r) {
            return l.apply(value);
        }

        @Override
        public <U> Either<L, U> map(Function<R, U> r) {
            return uncheckedCast(this);
        }
    }

    private static class Right<L, R> extends Either<L, R> {
        private final R value;

        public Right(R value) {
            this.value = value;
        }

        @Override
        public <U> U fold(Function<L, U> l, Function<R, U> r) {
            return r.apply(value);
        }

        @Override
        public <U> Either<L, U> map(Function<R, U> r) {
            return new Right<>(r.apply(value));
        }
    }
}
