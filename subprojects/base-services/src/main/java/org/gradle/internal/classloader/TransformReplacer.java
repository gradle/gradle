/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.IoActions;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.io.StreamByteBuffer;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TransformReplacer implements Closeable {
    private static final Loader SKIP_INSTRUMENTATION = new Loader();
    private final ConcurrentMap<ProtectionDomain, Loader> loaders;
    private final TransformedClassPath classPath;

    public TransformReplacer(TransformedClassPath classPath) {
        this.loaders = new ConcurrentHashMap<ProtectionDomain, Loader>();
        this.classPath = classPath;
    }

    @Nullable
    public byte[] getInstrumentedClass(@Nullable String className, ProtectionDomain protectionDomain) {
        if (className == null) {
            // JVM allows to define a class with "null" name through Unsafe. LambdaMetafactory in Java 8 defines a SAM implementation for method handle this way.
            return null;
        }
        try {
            return getLoader(protectionDomain).loadTransformedClass(className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Loader getLoader(@Nullable ProtectionDomain domain) {
        if (domain == null) {
            return SKIP_INSTRUMENTATION;
        }
        Loader transformLoader = loaders.get(domain);
        if (transformLoader == null) {
            File transformedJarPath = findTransformedFile(domain);
            Loader newLoader = transformedJarPath != null ? new JarLoader(transformedJarPath) : SKIP_INSTRUMENTATION;
            transformLoader = storeIfAbsent(domain, newLoader);
        }
        return transformLoader;
    }

    private Loader storeIfAbsent(ProtectionDomain domain, Loader newLoader) {
        Loader oldLoader = loaders.putIfAbsent(domain, newLoader);
        if (oldLoader != null) {
            // discard the new loader, someone beat us with storing it
            return oldLoader;
        }
        return newLoader;
    }

    @Override
    public void close() {
        for (Loader value : loaders.values()) {
            IoActions.closeQuietly(value);
        }
    }

    @Nullable
    private File findTransformedFile(ProtectionDomain protectionDomain) {
        CodeSource cs = protectionDomain.getCodeSource();
        URL originalUrl = cs != null ? cs.getLocation() : null;
        if (originalUrl == null || !"file".equals(originalUrl.getProtocol())) {
            // Cannot transform classes from anything but files
            return null;
        }
        try {
            return classPath.findTransformedJarFor(new File(originalUrl.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse file URL " + originalUrl, e);
        }
    }

    private static class Loader implements Closeable {
        @CheckForNull
        public byte[] loadTransformedClass(String className) throws IOException {
            return null;
        }

        @Override
        public void close() {}
    }

    private static class JarLoader extends Loader {
        private final File jarFilePath;
        private JarFile jarFile;

        public JarLoader(File transformedJarFile) {
            jarFilePath = transformedJarFile;
        }

        @Override
        @CheckForNull
        public synchronized byte[] loadTransformedClass(String className) throws IOException {
            JarFile jarFile = getJarFileLocked();
            JarEntry classEntry = jarFile.getJarEntry(classNameToPath(className));
            if (classEntry == null) {
                return null;
            }
            InputStream classBytes = jarFile.getInputStream(classEntry);
            try {
                return StreamByteBuffer.of(classBytes).readAsByteArray();
            } finally {
                classBytes.close();
            }
        }

        @Override
        public synchronized void close() {
            IoActions.closeQuietly(jarFile);
        }

        private JarFile getJarFileLocked() throws IOException {
            if (jarFile == null) {
                jarFile = new JarFile(jarFilePath);
            }
            return jarFile;
        }

        private static String classNameToPath(String className) {
            return className + ".class";
        }
    }
}
