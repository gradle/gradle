/*
 * Copyright 2018 the original author or authors.
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
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Try<T> {

    private Try() {
    }

    public static <U> Try<U> ofFailable(Callable<U> failable) {
        try {
            return Try.successful(failable.call());
        } catch (Exception e) {
            return Try.failure(e);
        }
    }

    public static <U> Try<U> successful(U u) {
        return new Success<U>(u);
    }

    public static <U> Try<U> failure(Throwable e) {
        return new Failure<U>(e);
    }

    public abstract T get();

    public abstract Optional<Throwable> getFailure();

    public abstract <U> Try<U> flatMap(Function<? super T, Try<U>> f);

    public <U> Try<U> map(final Function<? super T, U> f) {
        return flatMap(new Function<T, Try<U>>() {
            @Override
            public Try<U> apply(T input) {
                return Try.successful(f.apply(input));
            }
        });
    }

    public abstract Try<T> mapFailure(Function<Throwable, Throwable> f);

    public abstract void ifSuccessful(Consumer<T> consumer);

    private static class Success<T> extends Try<T> {
        private final T value;

        public Success(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Optional<Throwable> getFailure() {
            return Optional.empty();
        }

        @Override
        public <U> Try<U> flatMap(Function<? super T, Try<U>> f) {
            try {
                return f.apply(value);
            } catch (Exception e) {
                return Try.failure(e);
            }
        }

        @Override
        public Try<T> mapFailure(Function<Throwable, Throwable> f) {
            return this;
        }

        @Override
        public void ifSuccessful(Consumer<T> consumer) {
            consumer.accept(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Success<?> success = (Success<?>) o;

            return value.equals(success.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static class Failure<T> extends Try<T> {
        private final Throwable failure;

        public Failure(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public T get() {
            throw UncheckedException.throwAsUncheckedException(failure);
        }

        @Override
        public Optional<Throwable> getFailure() {
            return Optional.of(failure);
        }

        @Override
        public <U> Try<U> flatMap(Function<? super T, Try<U>> f) {
            return Try.failure(failure);
        }

        @Override
        public Try<T> mapFailure(Function<Throwable, Throwable> f) {
            return Try.failure(f.apply(failure));
        }

        @Override
        public void ifSuccessful(Consumer<T> consumer) {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Failure<?> failure1 = (Failure<?>) o;

            return failure.equals(failure1.failure);
        }

        @Override
        public int hashCode() {
            return failure.hashCode();
        }
    }
}
