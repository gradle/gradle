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

import org.gradle.internal.serialize.*;
import org.gradle.internal.dispatch.MethodInvocation;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MethodInvocationSerializer implements StatefulSerializer<MethodInvocation> {
    private final ClassLoader classLoader;
    private final MethodArgsSerializer methodArgsSerializer;

    public MethodInvocationSerializer(ClassLoader classLoader, MethodArgsSerializer methodArgsSerializer) {
        this.classLoader = classLoader;
        this.methodArgsSerializer = methodArgsSerializer;
    }

    public ObjectReader<MethodInvocation> newReader(Decoder decoder) {
        return new MethodInvocationReader(decoder, classLoader, methodArgsSerializer);
    }

    public ObjectWriter<MethodInvocation> newWriter(Encoder encoder) {
        return new MethodInvocationWriter(encoder, methodArgsSerializer);
    }

    private static class MethodDetails {
        final int methodId;
        final Method method;
        final Serializer<Object[]> argsSerializer;

        MethodDetails(int methodId, Method method, Serializer<Object[]> argsSerializer) {
            this.methodId = methodId;
            this.method = method;
            this.argsSerializer = argsSerializer;
        }
    }

    private static class MethodInvocationWriter implements ObjectWriter<MethodInvocation> {
        private final Encoder encoder;
        private final MethodArgsSerializer methodArgsSerializer;
        private final Map<Method, MethodDetails> methods = new HashMap<Method, MethodDetails>();

        MethodInvocationWriter(Encoder encoder, MethodArgsSerializer methodArgsSerializer) {
            this.encoder = encoder;
            this.methodArgsSerializer = methodArgsSerializer;
        }

        public void write(MethodInvocation value) throws Exception {
            if (value.getArguments().length != value.getMethod().getParameterTypes().length) {
                throw new IllegalArgumentException(String.format("Mismatched number of parameters to method %s.", value.getMethod()));
            }
            MethodDetails methodDetails = writeMethod(value.getMethod());
            writeArgs(methodDetails, value);
        }

        private void writeArgs(MethodDetails methodDetails, MethodInvocation value) throws Exception {
            methodDetails.argsSerializer.write(encoder, value.getArguments());
        }

        private MethodDetails writeMethod(Method method) throws IOException {
            MethodDetails methodDetails = methods.get(method);
            if (methodDetails == null) {
                int methodId = methods.size();
                methodDetails = new MethodDetails(methodId, method, methodArgsSerializer.forTypes(method.getParameterTypes()));
                methods.put(method, methodDetails);
                encoder.writeSmallInt(methodId);
                encoder.writeString(method.getDeclaringClass().getName());
                encoder.writeString(method.getName());
                encoder.writeSmallInt(method.getParameterTypes().length);
                for (int i = 0; i < method.getParameterTypes().length; i++) {
                    Class<?> paramType = method.getParameterTypes()[i];
                    encoder.writeString(paramType.getName());
                }
            } else {
                encoder.writeSmallInt(methodDetails.methodId);
            }
            return methodDetails;
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
        private final MethodArgsSerializer methodArgsSerializer;
        private final Map<Integer, MethodDetails> methods = new HashMap<Integer, MethodDetails>();

        MethodInvocationReader(Decoder decoder, ClassLoader classLoader, MethodArgsSerializer methodArgsSerializer) {
            this.decoder = decoder;
            this.classLoader = classLoader;
            this.methodArgsSerializer = methodArgsSerializer;
        }

        public MethodInvocation read() throws Exception {
            MethodDetails methodDetails = readMethod();
            Object[] args = readArguments(methodDetails);
            return new MethodInvocation(methodDetails.method, args);
        }

        private Object[] readArguments(MethodDetails methodDetails) throws Exception {
            return methodDetails.argsSerializer.read(decoder);
        }

        private MethodDetails readMethod() throws ClassNotFoundException, NoSuchMethodException, IOException {
            int methodId = decoder.readSmallInt();
            MethodDetails methodDetails = methods.get(methodId);
            if (methodDetails == null) {
                Class<?> declaringClass = readType();
                String methodName = decoder.readString();
                int paramCount = decoder.readSmallInt();
                Class<?>[] paramTypes = new Class<?>[paramCount];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = readType();
                }
                Method method = declaringClass.getDeclaredMethod(methodName, paramTypes);
                methodDetails = new MethodDetails(methodId, method, methodArgsSerializer.forTypes(method.getParameterTypes()));
                methods.put(methodId, methodDetails);
            }
            return methodDetails;
        }

        private Class<?> readType() throws ClassNotFoundException, IOException {
            String typeName = decoder.readString();
            Class<?> paramType = PRIMITIVE_TYPES.get(typeName);
            if (paramType == null) {
                paramType = Class.forName(typeName, false, classLoader);
            }
            return paramType;
        }
    }
}
