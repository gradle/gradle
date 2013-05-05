/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;

import java.io.InputStream;
import java.io.OutputStream;

public interface MessageSerializer<T> {
    /**
     * Creates a reader that can deserialize objects from the given input stream. Note that the implementation may perform buffering, and may consume any or all of the
     * content from the given input stream.
     */
    ObjectReader<T> newReader(InputStream inputStream, Address localAddress, Address remoteAddress);

    /**
     * Creates a writer that can write objects to the given output stream. Note that the implementation must not perform any buffering, so that after calling {@link ObjectWriter#write(Object)}
     * the serialized object has been flushed to the output stream.
     */
    ObjectWriter<T> newWriter(OutputStream outputStream);
}
