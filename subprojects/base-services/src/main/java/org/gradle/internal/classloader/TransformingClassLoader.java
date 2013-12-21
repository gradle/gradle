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

package org.gradle.internal.classloader;

import com.google.common.io.ByteStreams;
import org.gradle.api.GradleException;
import org.gradle.internal.classpath.ClassPath;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

public abstract class TransformingClassLoader extends MutableURLClassLoader {
    public TransformingClassLoader(ClassLoader parent, ClassPath classPath) {
        super(parent, classPath);
    }

    public TransformingClassLoader(ClassLoader parent, Collection<URL> urls) {
        super(parent, urls);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = findResource(name.replace(".", "/") + ".class");
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try {
            bytes = loadBytecode(resource);
            bytes = transform(bytes);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load class '%s' from %s.", name, resource), e);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    private byte[] loadBytecode(URL resource) throws IOException {
        InputStream inputStream = resource.openStream();
        try {
            return ByteStreams.toByteArray(inputStream);
        } finally {
            inputStream.close();
        }
    }

    protected abstract byte[] transform(byte[] bytes);
}
