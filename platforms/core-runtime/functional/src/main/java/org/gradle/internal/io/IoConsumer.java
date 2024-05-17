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

package org.gradle.internal.io;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * A variant of {@link Consumer} that is allowed to throw {@link IOException}.
 */
@FunctionalInterface
public interface IoConsumer<T> {
    void accept(@Nullable T payload) throws IOException;

    static <T> Consumer<T> wrap(IoConsumer<T> consumer) {
        return payload -> {
            try {
                consumer.accept(payload);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
