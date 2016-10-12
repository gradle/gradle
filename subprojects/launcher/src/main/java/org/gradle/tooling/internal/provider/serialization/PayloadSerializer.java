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

package org.gradle.tooling.internal.provider.serialization;

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

@ThreadSafe
public class PayloadSerializer {
    private final PayloadClassLoaderRegistry classLoaderRegistry;

    public PayloadSerializer(PayloadClassLoaderRegistry registry) {
        classLoaderRegistry = registry;
    }

    public SerializedPayload serialize(Object payload) {
        final SerializeMap map = classLoaderRegistry.newSerializeSession();
        try {
            StreamByteBuffer buffer = new StreamByteBuffer();
            final ObjectOutputStream objectStream = new ExceptionReplacingObjectOutputStream(buffer.getOutputStream()) {
                @Override
                protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
                    Class<?> targetClass = desc.forClass();
                    writeClass(targetClass);
                }

                @Override
                protected void annotateProxyClass(Class<?> cl) throws IOException {
                    writeInt(cl.getInterfaces().length);
                    for (Class<?> type : cl.getInterfaces()) {
                        writeClass(type);
                    }
                }

                private void writeClass(Class<?> targetClass) throws IOException {
                    writeClassLoader(targetClass);
                    writeUTF(targetClass.getName());
                }

                private void writeClassLoader(Class<?> targetClass) throws IOException {
                    writeShort(map.visitClass(targetClass));
                }
            };

            objectStream.writeObject(payload);
            objectStream.close();

            Map<Short, ClassLoaderDetails> classLoaders = new HashMap<Short, ClassLoaderDetails>();
            map.collectClassLoaderDefinitions(classLoaders);
            return new SerializedPayload(classLoaders, buffer.readAsListOfByteArrays());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public Object deserialize(SerializedPayload payload) {
        final DeserializeMap map = classLoaderRegistry.newDeserializeSession();
        try {
            final Map<Short, ClassLoaderDetails> classLoaderDetails = (Map<Short, ClassLoaderDetails>) payload.getHeader();
            StreamByteBuffer buffer = StreamByteBuffer.of(payload.getSerializedModel());
            final ObjectInputStream objectStream = new ExceptionReplacingObjectInputStream(buffer.getInputStream(), getClass().getClassLoader()) {
                @Override
                protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                    Class<?> aClass = readClass();
                    ObjectStreamClass descriptor = ObjectStreamClass.lookupAny(aClass);
                    if (descriptor == null) {
                        throw new ClassNotFoundException(aClass.getName());
                    }
                    return descriptor;
                }

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return desc.forClass();
                }

                private Class<?> readClass() throws IOException, ClassNotFoundException {
                    short id = readShort();
                    String className = readUTF();
                    ClassLoaderDetails classLoader = classLoaderDetails.get(id);
                    return map.resolveClass(classLoader, className);
                }

                @Override
                protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
                    int count = readInt();
                    Class<?>[] actualInterfaces = new Class<?>[count];
                    for (int i = 0; i < count; i++) {
                        actualInterfaces[i] = readClass();
                    }
                    return Proxy.getProxyClass(actualInterfaces[0].getClassLoader(), actualInterfaces);
                }
            };
            return objectStream.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
