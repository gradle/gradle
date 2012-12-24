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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;
import org.gradle.messaging.serialize.kryo.KryoAwareSerializer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MethodInvocationSerializer implements KryoAwareSerializer<MethodInvocation> {
    private final ClassLoader classLoader;

    public MethodInvocationSerializer(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ObjectReader<MethodInvocation> newReader(Input input) {
        return new MethodInvocationReader(input, classLoader);
    }

    public ObjectWriter<MethodInvocation> newWriter(Output output) {
        return new MethodInvocationWriter(output);
    }

    private static class MethodInvocationWriter implements ObjectWriter<MethodInvocation> {
        private final Output output;
        private final Map<Method, Integer> methods = new HashMap<Method, Integer>();

        public MethodInvocationWriter(Output output) {
            this.output = output;
        }

        public void write(MethodInvocation value) throws Exception {
            if (value.getArguments().length != value.getMethod().getParameterTypes().length) {
                throw new IllegalArgumentException(String.format("Mismatched number of parameters to method %s.", value.getMethod()));
            }
            writeMethod(value.getMethod());
            writeArguments(value);
        }

        private void writeArguments(MethodInvocation value) {
            Kryo kryo = new Kryo();
            for (Object arg : value.getArguments()) {
                kryo.writeClassAndObject(output, arg);
            }
        }

        private void writeMethod(Method method) {
            Integer methodId = methods.get(method);
            if (methodId == null) {
                methodId = methods.size();
                methods.put(method, methodId);
                output.writeInt(methodId, true);
                output.writeString(method.getDeclaringClass().getName());
                output.writeString(method.getName());
                output.writeInt(method.getParameterTypes().length, true);
                for (Class<?> paramType : method.getParameterTypes()) {
                    output.writeString(paramType.getName());
                }
            } else {
                output.writeInt(methodId, true);
            }
        }
    }

    private static class MethodInvocationReader implements ObjectReader<MethodInvocation> {
        private static final Map<String, Class<?>> PRIMITIVE_TYPES;
        static {
            PRIMITIVE_TYPES = new HashMap<String, Class<?>>();
            PRIMITIVE_TYPES.put(Integer.TYPE.getName(), Integer.TYPE);
        }

        private final Input input;
        private final ClassLoader classLoader;
        private final Map<Integer, Method> methods = new HashMap<Integer, Method>();

        public MethodInvocationReader(Input input, ClassLoader classLoader) {
            this.input = input;
            this.classLoader = classLoader;
        }

        public MethodInvocation read() throws Exception {
            Method method = readMethod();
            Object[] args = readArguments(method);
            return new MethodInvocation(method, args);
        }

        private Object[] readArguments(Method method) {
            Object[] args = new Object[method.getParameterTypes().length];
            Kryo kryo = new Kryo();
            for (int i = 0; i < args.length; i++) {
                args[i] = kryo.readClassAndObject(input);
            }
            return args;
        }

        private Method readMethod() throws ClassNotFoundException, NoSuchMethodException {
            int methodId = input.readInt(true);
            Method method = methods.get(methodId);
            if (method == null) {
                Class<?> declaringClass = readType();
                String methodName = input.readString();
                int paramCount = input.readInt(true);
                Class<?>[] paramTypes = new Class<?>[paramCount];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = readType();
                }
                method = declaringClass.getDeclaredMethod(methodName, paramTypes);
                methods.put(methodId, method);
            }
            return method;
        }

        private Class<?> readType() throws ClassNotFoundException {
            String typeName = input.readString();
            Class<?> paramType = PRIMITIVE_TYPES.get(typeName);
            if (paramType == null) {
                paramType = classLoader.loadClass(typeName);
            }
            return paramType;
        }
    }
}
