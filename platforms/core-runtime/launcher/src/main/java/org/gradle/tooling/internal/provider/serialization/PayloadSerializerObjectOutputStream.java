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

import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;
import org.gradle.internal.serialize.TopLevelExceptionPlaceholder;

import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

class PayloadSerializerObjectOutputStream extends ExceptionReplacingObjectOutputStream {
    static final int SAME_CLASSLOADER_TOKEN = 0;
    private final SerializeMap map;

    public PayloadSerializerObjectOutputStream(OutputStream outputStream, SerializeMap map) throws IOException {
        super(outputStream);
        this.map = map;
    }

    @Override
    protected ExceptionReplacingObjectOutputStream createNewInstance(OutputStream outputStream) throws IOException {
        return new PayloadSerializerObjectOutputStream(outputStream, map);
    }

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
        if (TopLevelExceptionPlaceholder.class.getPackage().equals(targetClass.getPackage())) {
            writeShort(SAME_CLASSLOADER_TOKEN);
        } else {
            writeShort(map.visitClass(targetClass));
        }
    }
}
