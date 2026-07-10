/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.modelreader.api;

import org.gradle.internal.instrumentation.model.CallInterceptionRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public interface CallInterceptionRequestReader<T> {

    /**
     * @param input the input context to read
     * @param context the context that is shared between request reads, can be used for caching
     */
    Collection<Result> readRequest(T input, ReadRequestContext context);

    class ReadRequestContext {
        private final Map<String, Object> store = new HashMap<>();

        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(String key, Function<String, T> function) {
            return (T) store.computeIfAbsent(key, __ -> checkNotNull(function.apply(key)));
        }

        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(String key) {
            return Optional.ofNullable((T) store.get(key));
        }
    }

    interface Result {
        class Success implements Result {
            private final CallInterceptionRequest request;

            public Success(CallInterceptionRequest request) {
                this.request = request;
            }

            public CallInterceptionRequest getRequest() {
                return request;
            }
        }

        class InvalidRequest implements Result {
            public final String reason;

            public InvalidRequest(String reason) {
                this.reason = reason;
            }
        }
    }
}
