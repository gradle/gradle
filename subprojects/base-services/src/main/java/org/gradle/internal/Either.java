/*
 * Copyright 2019 the original author or authors.
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
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Either<L, R> {

    private Either() {
    }

    public static <L, R> Either<L, R> left(L left) {
        return new Left<L, R>(left);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Right<L, R>(right);
    }

    public abstract L getLeft();

    public abstract R getRight();

    public abstract boolean isLeft();

    public abstract boolean isRight();

    public abstract <T> T get(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends T> transformRight);

    public abstract void apply(Consumer<? super L> applyLeft, Consumer<? super R> applyRight);

    public abstract <T, U> Either<T, U> map(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends U> transformRight);

    private final static class Left<L, R> extends Either<L, R> {

        private final L leftValue;

        public Left(L left) {
            leftValue = left;
        }

        @Override
        public L getLeft() {
            return leftValue;
        }

        @Override
        public R getRight() {
            throw new NoSuchElementException("Tried to getRight from a Left");
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
        public <T> T get(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends T> transformRight) {
            return transformLeft.apply(leftValue);
        }

        @Override
        public <T, U> Either<T, U> map(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends U> transformRight) {
            return Either.left(transformLeft.apply(leftValue));
        }

        @Override
        public void apply(Consumer<? super L> applyLeft, Consumer<? super R> applyRight) {
            applyLeft.accept(leftValue);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Left<?, ?> left = (Left<?, ?>) o;
            return leftValue.equals(left.leftValue);
        }

        @Override
        public int hashCode() {
            return leftValue.hashCode();
        }
    }

    private final static class Right<L, R> extends Either<L, R> {

        private final R rightValue;

        public Right(R right) {
            rightValue = right;
        }

        @Override
        public L getLeft() {
            throw new NoSuchElementException("Tried to getLeft from a Right");
        }

        @Override
        public R getRight() {
            return rightValue;
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
        public <T> T get(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends T> transformRight) {
            return transformRight.apply(rightValue);
        }

        @Override
        public <T, U> Either<T, U> map(Function<? super L, ? extends T> transformLeft, Function<? super R, ? extends U> transformRight) {
            return Either.right(transformRight.apply(rightValue));
        }

        @Override
        public void apply(Consumer<? super L> applyLeft, Consumer<? super R> applyRight) {
            applyRight.accept(rightValue);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Right<?, ?> right = (Right<?, ?>) o;
            return rightValue.equals(right.rightValue);
        }

        @Override
        public int hashCode() {
            return rightValue.hashCode();
        }
    }
}
