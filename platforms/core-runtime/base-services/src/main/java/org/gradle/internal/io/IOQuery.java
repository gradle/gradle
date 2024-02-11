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
package org.gradle.internal.io;

import java.io.IOException;

public interface IOQuery<T> {
    Result<T> run() throws IOException, InterruptedException;

    abstract class Result<T> {
        public abstract boolean isSuccessful();

        public abstract T getValue();

        /**
         * Creates a result that indicates that the operation was successful and should not be repeated.
         */
        public static <T> Result<T> successful(final T value) {
            if (value == null) {
                throw new IllegalArgumentException();
            }
            return new Result<T>() {
                @Override
                public boolean isSuccessful() {
                    return true;
                }

                @Override
                public T getValue() {
                    return value;
                }
            };
        }

        /**
         * Creates a result that indicates that the operation was not successful and should be repeated.
         */
        public static <T> Result<T> notSuccessful(final T value) {
            if (value == null) {
                throw new IllegalArgumentException();
            }
            return new Result<T>() {
                @Override
                public boolean isSuccessful() {
                    return false;
                }

                @Override
                public T getValue() {
                    return value;
                }
            };
        }
    }
}
