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

package org.gradle.messaging.serialize.kryo;

import com.esotericsoftware.kryo.io.Input;
import org.gradle.messaging.serialize.Decoder;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class KryoBackedDecoder implements Decoder {
    private final Input input;

    /**
     * Note that this decoder uses buffering, so will attempt to read beyond the end of the encoded data.
     * This means you should use this type only when this decoder will be used to decode the entire stream.
     */
    public KryoBackedDecoder(InputStream inputStream) {
        input = new Input(inputStream);
    }

    public InputStream getInputStream() {
        return input;
    }

    public long readLong() throws EOFException, IOException {
        return input.readLong();
    }
}
