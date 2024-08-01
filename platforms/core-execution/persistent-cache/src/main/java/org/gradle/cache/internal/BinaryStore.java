/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.Closeable;

public interface BinaryStore {
    void write(WriteAction write);

    //done writing data, release any resources
    BinaryData done();

    interface WriteAction {
        void write(Encoder encoder) throws Exception;
    }

    interface ReadAction<T> {
        T read(Decoder decoder) throws Exception;
    }

    interface BinaryData extends Closeable {
        <T> T read(ReadAction<T> readAction);
    }
}
