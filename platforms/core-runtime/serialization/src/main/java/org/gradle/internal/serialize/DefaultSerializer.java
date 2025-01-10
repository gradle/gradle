/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import org.gradle.api.NonNullApi;
import org.gradle.internal.Cast;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;

public class DefaultSerializer<T> extends AbstractSerializer<T> {
    @NonNullApi
    public interface StreamFactory<T, U> {
        U create(T t, ClassLoader classLoader) throws IOException;
    }

    private ClassLoader classLoader;
    private final StreamFactory<OutputStream, ObjectOutputStream> objectOutputStreamFactory;
    private final StreamFactory<InputStream, ObjectInputStream> inputStreamStreamFactory;

    public DefaultSerializer(StreamFactory<OutputStream, ObjectOutputStream> objectOutputStreamFactory, StreamFactory<InputStream, ObjectInputStream> inputStreamStreamFactory) {
        this.classLoader = getClass().getClassLoader();
        this.objectOutputStreamFactory = objectOutputStreamFactory;
        this.inputStreamStreamFactory = inputStreamStreamFactory;
    }

    public DefaultSerializer() {
        this.classLoader = getClass().getClassLoader();
        this.objectOutputStreamFactory = new OutputStreamObjectOutputStreamStreamFactory();
        this.inputStreamStreamFactory = new InputStreamObjectInputStreamStreamFactory();
    }


    public DefaultSerializer(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : getClass().getClassLoader();
        this.objectOutputStreamFactory = new OutputStreamObjectOutputStreamStreamFactory();
        this.inputStreamStreamFactory = new InputStreamObjectInputStreamStreamFactory();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public T read(Decoder decoder) throws Exception {
        try {
            return Cast.uncheckedNonnullCast(inputStreamStreamFactory.create(decoder.getInputStream(), classLoader).readObject());
        } catch (StreamCorruptedException e) {
            return null;
        }
    }

    @Override
    public void write(Encoder encoder, T value) throws IOException {
        ObjectOutputStream objectStr = objectOutputStreamFactory.create(encoder.getOutputStream(), classLoader);
        objectStr.writeObject(value);
        objectStr.flush();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        DefaultSerializer<?> rhs = (DefaultSerializer<?>) obj;
        return Objects.equal(classLoader, rhs.classLoader);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), classLoader);
    }

    @NonNullApi
    private static class OutputStreamObjectOutputStreamStreamFactory implements StreamFactory<OutputStream, ObjectOutputStream> {
        @Override
        public ObjectOutputStream create(OutputStream outputStream, ClassLoader classLoaderNotUsed) throws IOException {
            return new ObjectOutputStream(outputStream);
        }
    }

    @NonNullApi
    private static class InputStreamObjectInputStreamStreamFactory implements StreamFactory<InputStream, ObjectInputStream> {

        @Override
        public ObjectInputStream create(InputStream inputStream, ClassLoader classLoader) throws IOException {
            return new ClassLoaderObjectInputStream(inputStream, classLoader);
        }
    }
}
