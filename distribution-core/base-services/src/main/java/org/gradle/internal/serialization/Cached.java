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

import org.gradle.internal.Try;

import java.util.concurrent.Callable;

/**
 * Represents a computation that must execute only once and
 * whose result must be cached even (or specially) at serialization time.
 *
 * @param <T> the resulting type
 */
public abstract class Cached<T> {

    public static <T> Cached<T> of(Callable<T> computation) {
        return new Deferred<T>(computation);
    }

    public abstract T get();

    private static class Deferred<T> extends Cached<T> implements java.io.Serializable {

        private Callable<T> computation;
        private Try<T> result;

        public Deferred(Callable<T> computation) {
            this.computation = computation;
        }

        @Override
        public T get() {
            return result().get();
        }

        private Try<T> result() {
            if (result == null) {
                result = Try.ofFailable(computation);
                computation = null;
            }
            return result;
        }

        private Object writeReplace() {
            return new Fixed<T>(result());
        }
    }

    private static class Fixed<T> extends Cached<T> {

        private final Try<T> result;

        public Fixed(Try<T> result) {
            this.result = result;
        }

        @Override
        public T get() {
            return result.get();
        }
    }
}
