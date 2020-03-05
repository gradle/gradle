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

package org.gradle.process.internal.worker.request;

import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Message;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SerializerRegistry;

import java.util.List;

public class RequestSerializer implements Serializer<Request> {
    private final List<SerializerRegistry> registries;
    private final ClassLoader classLoader;
    private final JavaObjectSerializer javaObjectSerializer;

    public RequestSerializer(ClassLoader classLoader, List<SerializerRegistry> registries) {
        this.registries = registries;
        this.classLoader = classLoader;
        this.javaObjectSerializer = new JavaObjectSerializer(classLoader);
    }

    @Override
    public void write(Encoder encoder, Request request) throws Exception {
        Object object = request.getArg();
        if (object == null) {
            encoder.writeString(NullType.class.getName());
        } else {
            Class<?> type = object.getClass();
            encoder.writeString(type.getName());
            select(type).write(encoder, object);
        }

        javaObjectSerializer.write(encoder, request.getBuildOperation());
    }

    @Override
    public Request read(Decoder decoder) throws Exception {
        Class<?> type = classLoader.loadClass(decoder.readString());
        Object arg;
        if (type == NullType.class) {
            arg = null;
        } else {
            arg = select(type).read(decoder);
        }

        BuildOperationRef buildOperation = (BuildOperationRef) javaObjectSerializer.read(decoder);

        return new Request(arg, buildOperation);
    }

    private Serializer<Object> select(Class<?> type) {
        for (SerializerRegistry registry : registries) {
            if (registry.canSerialize(type)) {
                return Cast.uncheckedCast(registry.build(type));
            }
        }
        return javaObjectSerializer;
    }

    private static class NullType {
    }

    private static class JavaObjectSerializer implements Serializer<Object> {
        private final ClassLoader classLoader;

        public JavaObjectSerializer(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Object read(Decoder decoder) throws Exception {
            return Message.receive(decoder.getInputStream(), classLoader);
        }

        @Override
        public void write(Encoder encoder, Object value) throws Exception {
            Message.send(value, encoder.getOutputStream());
        }
    }
}
