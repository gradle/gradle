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

package org.gradle.cache.internal.streams;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.Nullable;

public interface ValueStore<T> {
    /**
     * Writes the given value and returns an address for the written block.
     * The current thread performs the encoding. The implementation may perform some buffering and this may not necessarily be
     * flushed to the filesystem on completion of this method.
     */
    BlockAddress write(@Nullable T value);

    /**
     * Reads the contents of the given block.
     * The current thread performs the decoding.
     */
    T read(BlockAddress blockAddress);

    interface Writer<T> {
        void write(Encoder encoder, T value) throws Exception;
    }

    interface Reader<T> {
        @Nullable
        T read(Decoder decoder) throws Exception;
    }
}
