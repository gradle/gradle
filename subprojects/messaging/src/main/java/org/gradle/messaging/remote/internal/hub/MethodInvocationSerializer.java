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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;
import org.gradle.messaging.serialize.kryo.StatefulSerializer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MethodInvocationSerializer implements StatefulSerializer<MethodInvocation> {
    private final ClassLoader classLoader;
    private final StatefulSerializer<Object[]> argsSerializer;

    public MethodInvocationSerializer(ClassLoader classLoader, StatefulSerializer<Object[]> argsSerializer) {
        this.classLoader = classLoader;
        this.argsSerializer = argsSerializer;
    }

    public ObjectReader<MethodInvocation> newReader(Decoder decoder) {
        return new MethodInvocationReader(decoder, classLoader, argsSerializer.newReader(decoder));
    }

    public ObjectWriter<MethodInvocation> newWriter(Encoder encoder) {
        return new MethodInvocationWriter(encoder, argsSerializer.newWriter(encoder));
    }

    private static class MethodInvocationWriter implements ObjectWriter<MethodInvocation> {
        private final Encoder encoder;
        private final ObjectWriter<Object[]> argsWriter;
        private final Map<Method, Integer> methods = new HashMap<Method, Integer>();

        public MethodInvocationWriter(Encoder encoder, ObjectWriter<Object[]> argsWriter) {
            this.encoder = encoder;
            this.argsWriter = argsWriter;
        }

        public void write(MethodInvocation value) throws Exception {
            if (value.getArguments().length != value.getMethod().getParameterTypes().length) {
                throw new IllegalArgumentException(String.format("Mismatched number of parameters to method %s.", value.getMethod()));
            }
            writeMethod(value.getMethod());
            writeArguments(value);
        }

        private void writeArguments(MethodInvocation value) throws Exception {
            argsWriter.write(value.getArguments());
        }

        private void writeMethod(Method method) throws IOException {
            Integer methodId = methods.get(method);
            if (methodId == null) {
                methodId = methods.size();
                methods.put(method, methodId);
                encoder.writeSmallInt(methodId);
                encoder.writeString(method.getDeclaringClass().getName());
                encoder.writeString(method.getName());
                encoder.writeSmallInt(method.getParameterTypes().length);
                for (Class<?> paramType : method.getParameterTypes()) {
                    encoder.writeString(paramType.getName());
                }
            } else {
                encoder.writeSmallInt(methodId);
            }
        }
    }

    private static class MethodInvocationReader implements ObjectReader<MethodInvocation> {
        private static final Map<String, Class<?>> PRIMITIVE_TYPES;
        static {
            PRIMITIVE_TYPES = new HashMap<String, Class<?>>();
            PRIMITIVE_TYPES.put(Integer.TYPE.getName(), Integer.TYPE);
        }

        private final Decoder decoder;
        private final ClassLoader classLoader;
        private final ObjectReader<Object[]> argsReader;
        private final Map<Integer, Method> methods = new HashMap<Integer, Method>();

        public MethodInvocationReader(Decoder decoder, ClassLoader classLoader, ObjectReader<Object[]> argsReader) {
            this.decoder = decoder;
            this.classLoader = classLoader;
            this.argsReader = argsReader;
        }

        public MethodInvocation read() throws Exception {
            Method method = readMethod();
            Object[] args = readArguments();
            return new MethodInvocation(method, args);
        }

        private Object[] readArguments() throws Exception {
            return argsReader.read();
        }

        private Method readMethod() throws ClassNotFoundException, NoSuchMethodException, IOException {
            int methodId = decoder.readSmallInt();
            Method method = methods.get(methodId);
            if (method == null) {
                Class<?> declaringClass = readType();
                String methodName = decoder.readString();
                int paramCount = decoder.readSmallInt();
                Class<?>[] paramTypes = new Class<?>[paramCount];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = readType();
                }
                method = declaringClass.getDeclaredMethod(methodName, paramTypes);
                methods.put(methodId, method);
            }
            return method;
        }

        private Class<?> readType() throws ClassNotFoundException, IOException {
            String typeName = decoder.readString();
            Class<?> paramType = PRIMITIVE_TYPES.get(typeName);
            if (paramType == null) {
                paramType = classLoader.loadClass(typeName);
            }
            return paramType;
        }
    }
}
