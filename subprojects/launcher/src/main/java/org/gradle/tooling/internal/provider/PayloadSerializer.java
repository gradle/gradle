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

package org.gradle.tooling.internal.provider;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.tooling.internal.provider.jdk6.Jdk6ClassLookup;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ThreadSafe
public class PayloadSerializer {
    private static final short SYSTEM_CLASS_LOADER_ID = (short) -1;
    private static final Set<ClassLoader> SYSTEM_CLASS_LOADERS = new HashSet<ClassLoader>();
    private final Transformer<ObjectStreamClass, Class<?>> classLookup;
    private final PayloadClassLoaderRegistry classLoaderRegistry;

    static {
        for (ClassLoader cl = ClassLoader.getSystemClassLoader().getParent(); cl != null; cl = cl.getParent()) {
            SYSTEM_CLASS_LOADERS.add(cl);
        }
    }

    public PayloadSerializer(PayloadClassLoaderRegistry registry) {
        classLoaderRegistry = registry;
        classLookup = new Jdk6ClassLookup();
    }

    public SerializedPayload serialize(Object payload) {
        final SerializeMap map = classLoaderRegistry.newSerializeSession();
        try {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            final ObjectOutputStream objectStream = new ObjectOutputStream(content) {
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
                    ClassLoader classLoader = targetClass.getClassLoader();
                    if (classLoader == null || SYSTEM_CLASS_LOADERS.contains(classLoader)) {
                        writeShort(SYSTEM_CLASS_LOADER_ID);
                    } else {
                        writeShort(map.visitClass(targetClass));
                    }
                }
            };

            objectStream.writeObject(payload);
            objectStream.close();

            Map<Short, ClassLoaderDetails> classLoaders = map.getClassLoaders();
            if (classLoaders.containsKey(SYSTEM_CLASS_LOADER_ID)) {
                throw new IllegalArgumentException("Unexpected ClassLoader id found");
            }
            return new SerializedPayload(classLoaders, content.toByteArray());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public Object deserialize(SerializedPayload payload) {
        final DeserializeMap map = classLoaderRegistry.newDeserializeSession();
        try {
            final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader().getParent();
            final Map<Short, ClassLoaderDetails> classLoaderDetails = (Map<Short, ClassLoaderDetails>) payload.getHeader();

            final ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(payload.getSerializedModel())) {
                @Override
                protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                    Class<?> aClass = readClass();
                    ObjectStreamClass descriptor = classLookup.transform(aClass);
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
                    if (id == SYSTEM_CLASS_LOADER_ID) {
                        return Class.forName(className, false, systemClassLoader);
                    }
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
