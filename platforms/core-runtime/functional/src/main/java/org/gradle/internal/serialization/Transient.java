/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.serialization;

import javax.annotation.Nullable;

/**
 * A value that gets discarded during serialization.
 */
public abstract class Transient<T> implements java.io.Serializable {

    /**
     * A mutable variable that gets discarded during serialization.
     */
    public static abstract class Var<T> extends Transient<T> {
        public abstract void set(T value);
    }

    public static <T> Transient<T> of(T value) {
        return new ImmutableTransient<>(value);
    }

    public static <T> Var<T> varOf() {
        return varOf(null);
    }

    public static <T> Var<T> varOf(@Nullable T value) {
        return new MutableTransient<>(value);
    }

    @Nullable
    public abstract T get();

    public boolean isPresent() {
        return true;
    }

    private static class ImmutableTransient<T> extends Transient<T> {

        private final T value;

        public ImmutableTransient(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        private Object writeReplace() {
            return DISCARDED;
        }
    }

    private static class MutableTransient<T> extends Var<T> {

        @Nullable
        private T value;

        public MutableTransient(@Nullable T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void set(T value) {
            this.value = value;
        }

        private Object writeReplace() {
            return DISCARDED;
        }
    }

    private static class Discarded<T> extends Var<T> {

        @Override
        public void set(T value) {
            throw new IllegalStateException("The value of this property cannot be set after it has been discarded during serialization.");
        }

        @Override
        public T get() {
            throw new IllegalStateException("The value of this property has been discarded during serialization.");
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        private Object readResolve() {
            return DISCARDED;
        }
    }

    @SuppressWarnings("ClassInitializationDeadlock")
    private static final Transient<Object> DISCARDED = new Discarded<>();
}
