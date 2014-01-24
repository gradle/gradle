/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.messaging.remote.internal.Message;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;

public class JavaSerializer<T> implements StatefulSerializer<T> {
    private final ClassLoader classLoader;

    public JavaSerializer(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ObjectReader<T> newReader(Decoder decoder) {
        return new JavaReader<T>(decoder, classLoader);
    }

    public ObjectWriter<T> newWriter(Encoder encoder) {
        return new JavaWriter<T>(encoder);
    }

    private static class JavaReader<T> implements ObjectReader<T> {
        private final Decoder decoder;
        private final ClassLoader classLoader;

        private JavaReader(Decoder decoder, ClassLoader classLoader) {
            this.decoder = decoder;
            this.classLoader = classLoader;
        }

        public T read() throws Exception {
            return (T) Message.receive(decoder.getInputStream(), classLoader);
        }
    }

    private class JavaWriter<T> implements ObjectWriter<T> {
        private final Encoder encoder;

        public JavaWriter(Encoder encoder) {
            this.encoder = encoder;
        }

        public void write(T value) throws Exception {
            Message.send(value, encoder.getOutputStream());
        }
    }
}
