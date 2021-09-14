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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object to represent the result of an operation that can potentially fail.
 * The object either holds the result of a successful execution, or an exception encountered during a failed one.
 */
public abstract class Try<T> {

    private Try() {
    }

    /**
     * Construct a {@code Try} by executing the given operation.
     * The returned object will either hold the result or the exception thrown during the operation.
     */
    public static <U> Try<U> ofFailable(Callable<U> failable) {
        try {
            return Try.successful(failable.call());
        } catch (Exception e) {
            return Try.failure(e);
        }
    }

    /**
     * Construct a {@code Try} representing a successful execution.
     * The returned object will hold the given result.
     */
    public static <U> Try<U> successful(U result) {
        return new Success<>(result);
    }

    /**
     * Construct a {@code Try} representing a failed execution.
     * The returned object will hold the given failure.
     */
    public static <U> Try<U> failure(Throwable failure) {
        return new Failure<>(failure);
    }

    /**
     * Returns whether this {@code Try} represents a successful execution.
     */
    public abstract boolean isSuccessful();

    /**
     * Return the result if the represented operation was successful.
     * Throws the original failure otherwise (wrapped in an {@code UncheckedException} if necessary).
     */
    public abstract T get();

    /**
     * Return the result if the represented operation was successful or return the result of the given function.
     * In the latter case the failure is passed to the function.
     */
    public abstract T getOrMapFailure(Function<Throwable, T> f);

    /**
     * Returns the failure for a failed result, or {@link Optional#empty()} otherwise.
     */
    public abstract Optional<Throwable> getFailure();

    /**
     * If the represented operation was successful, returns the result of applying the given
     * {@code Try}-bearing mapping function to the value, otherwise returns
     * the {@code Try} representing the original failure.
     *
     * Exceptions thrown by the given function are propagated.
     */
    public abstract <U> Try<U> flatMap(Function<? super T, Try<U>> f);

    /**
     * If the represented operation was successful, returns the result of applying the given
     * mapping function to the value, otherwise returns
     * the {@code Try} representing the original failure.
     *
     * This is similar to {@link #tryMap(Function)} but propagates any exception the given function throws.
     */
    public abstract <U> Try<U> map(Function<? super T, U> f);

    /**
     * If the represented operation was successful, returns the result of applying the given
     * mapping function to the value, otherwise returns
     * the {@code Try} representing the original failure.
     *
     * This is similar to {@link #map(Function)} but converts any exception the given function
     * throws into a failed {@code Try}.
     */
    public abstract <U> Try<U> tryMap(Function<? super T, U> f);

    /**
     * If the represented operation was successful, returns the original result,
     * otherwise returns the given mapping function applied to the failure.
     */
    public abstract Try<T> mapFailure(Function<Throwable, Throwable> f);

    /**
     * Calls the given consumer with the result iff the represented operation was successful.
     */
    public abstract void ifSuccessful(Consumer<T> consumer);

    /**
     * Calls {successConsumer} with the result if the represented operation was successful,
     * otherwise calls {failureConsumer} with the failure.
     */
    public abstract void ifSuccessfulOrElse(Consumer<? super T> successConsumer, Consumer<? super Throwable> failureConsumer);

    private static final class Success<T> extends Try<T> {
        private final T value;

        public Success(T value) {
            this.value = value;
        }

        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T getOrMapFailure(Function<Throwable, T> f) {
            return value;
        }

        @Override
        public Optional<Throwable> getFailure() {
            return Optional.empty();
        }

        @Override
        public <U> Try<U> flatMap(Function<? super T, Try<U>> f) {
            return f.apply(value);
        }

        @Override
        public <U> Try<U> map(Function<? super T, U> f) {
            return Try.successful(f.apply(value));
        }

        @Override
        public <U> Try<U> tryMap(final Function<? super T, U> f) {
            return Try.ofFailable(() -> f.apply(value));
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
        public void ifSuccessfulOrElse(Consumer<? super T> successConsumer, Consumer<? super Throwable> failureConsumer) {
            successConsumer.accept(value);
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

        @Override
        public String toString() {
            return "Successful(" + value + ")";
        }
    }

    private static final class Failure<T> extends Try<T> {
        private final Throwable failure;

        public Failure(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public T get() {
            // TODO Merge back with org.gradle.internal.UncheckedException.throwAsUncheckedException()
            //      once it's extracted from :base-services
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (failure instanceof IOException) {
                throw new UncheckedIOException((IOException) failure);
            }
            throw new RuntimeException(failure);
        }

        @Override
        public T getOrMapFailure(Function<Throwable, T> f) {
            return f.apply(failure);
        }

        @Override
        public Optional<Throwable> getFailure() {
            return Optional.of(failure);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Try<U> flatMap(Function<? super T, Try<U>> f) {
            return (Try<U>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Try<U> map(Function<? super T, U> f) {
            return (Try<U>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Try<U> tryMap(Function<? super T, U> f) {
            return (Try<U>) this;
        }

        @Override
        public Try<T> mapFailure(Function<Throwable, Throwable> f) {
            return Try.failure(f.apply(failure));
        }

        @Override
        public void ifSuccessful(Consumer<T> consumer) {
        }

        @Override
        public void ifSuccessfulOrElse(Consumer<? super T> successConsumer, Consumer<? super Throwable> failureConsumer) {
            failureConsumer.accept(failure);
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

        @Override
        public String toString() {
            return "Failed(" + failure + ")";
        }
    }
}
