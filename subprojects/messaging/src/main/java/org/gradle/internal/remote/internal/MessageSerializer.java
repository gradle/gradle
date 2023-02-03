/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.FlushableEncoder;

import java.io.InputStream;
import java.io.OutputStream;

public interface MessageSerializer {
    /**
     * Creates a decoder that reads from the given input stream. Note that the implementation may perform buffering, and may consume any or all of the
     * content from the given input stream.
     */
    Decoder newDecoder(InputStream inputStream);

    /**
     * Creates an encoder that writes the given output stream. Note that the implementation may perform buffering.
     */
    FlushableEncoder newEncoder(OutputStream outputStream);
}
