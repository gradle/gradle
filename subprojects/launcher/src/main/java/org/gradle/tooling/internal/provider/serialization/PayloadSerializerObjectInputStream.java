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

import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.gradle.tooling.internal.provider.serialization.PayloadSerializerObjectOutputStream.SAME_CLASSLOADER_TOKEN;

class PayloadSerializerObjectInputStream extends ExceptionReplacingObjectInputStream {
    private final Map<Short, ClassLoaderDetails> classLoaderDetails;
    private final DeserializeMap map;

    public PayloadSerializerObjectInputStream(InputStream inputStream, ClassLoader classLoader, Map<Short, ClassLoaderDetails> classLoaderDetails, DeserializeMap map) throws IOException {
        super(inputStream, classLoader);
        this.classLoaderDetails = classLoaderDetails;
        this.map = map;
    }

    @Override
    protected ExceptionReplacingObjectInputStream createNewInstance(InputStream inputStream) throws IOException {
        return new PayloadSerializerObjectInputStream(inputStream, getClassLoader(), classLoaderDetails, map);
    }

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
        if (id == SAME_CLASSLOADER_TOKEN) {
            return super.lookupClass(className);
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

    @Override
    protected Class<?> lookupClass(String type) throws ClassNotFoundException {
        try {
            return super.lookupClass(type);
        } catch (ClassNotFoundException e) {
            // lookup class in all classloaders
            for (ClassLoaderDetails details : classLoaderDetails.values()) {
                try {
                    return map.resolveClass(details, type);
                } catch (ClassNotFoundException ignored) {
                    // ignore
                }
            }
            throw e;
        }
    }
}
