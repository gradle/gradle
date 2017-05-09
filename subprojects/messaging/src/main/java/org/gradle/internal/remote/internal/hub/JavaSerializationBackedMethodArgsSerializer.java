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

package org.gradle.internal.remote.internal.hub;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Message;
import org.gradle.internal.serialize.Serializer;

class JavaSerializationBackedMethodArgsSerializer implements MethodArgsSerializer {
    private static final Object[] ZERO_ARGS = new Object[0];
    private final ClassLoader classLoader;

    public JavaSerializationBackedMethodArgsSerializer(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Serializer<Object[]> forTypes(Class<?>[] types) {
        if (types.length == 0) {
            return new EmptyArraySerializer();
        }
        return new ArraySerializer();
    }

    private class EmptyArraySerializer implements Serializer<Object[]> {
        @Override
        public Object[] read(Decoder decoder) {
            return ZERO_ARGS;
        }

        @Override
        public void write(Encoder encoder, Object[] value) {
        }
    }

    private class ArraySerializer implements Serializer<Object[]> {
        @Override
        public Object[] read(Decoder decoder) throws Exception {
            return (Object[]) Message.receive(decoder.getInputStream(), classLoader);
        }

        @Override
        public void write(Encoder encoder, Object[] value) throws Exception {
            Message.send(value, encoder.getOutputStream());
        }
    }
}
