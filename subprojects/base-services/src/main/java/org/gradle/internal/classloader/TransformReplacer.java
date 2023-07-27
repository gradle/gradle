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

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.IoActions;
import org.gradle.internal.agents.InstrumentingClassLoader;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.io.StreamByteBuffer;

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

/**
 * A helper class that can remap classes loaded from the original JARs of the TransformedClassPath to the classes from the corresponding transformed JARs.
 * <p>
 * This class is thread-safe.
 */
public class TransformReplacer implements Closeable {
    public static final String MARKER_RESOURCE_NAME = TransformReplacer.class.getName() + ".transformed";

    private static final Loader SKIP_INSTRUMENTATION = new Loader();
    private final ConcurrentMap<ProtectionDomain, Loader> loaders;
    private final TransformedClassPath classPath;
    private volatile boolean closed;

    public TransformReplacer(TransformedClassPath classPath) {
        this.loaders = new ConcurrentHashMap<ProtectionDomain, Loader>();
        this.classPath = classPath;
    }

    /**
     * Returns the transformed bytecode for the {@code className} loaded from {@code protectionDomain} if it is available in the classpath or {@code null} otherwise.
     *
     * @param className the name of the class (in internal binary format, e.g. {@code java/util/List}
     * @param protectionDomain the protection domain of the class
     * @return transformed bytes or {@code null} if there is no transformation for this class
     *
     * @see InstrumentingClassLoader#instrumentClass(String, ProtectionDomain, byte[])
     */
    @Nullable
    public byte[] getInstrumentedClass(@Nullable String className, @Nullable ProtectionDomain protectionDomain) {
        // Even though some classes don't need instrumentation, trying to query the closed TransformReplacer is still an error.
        ensureOpened();

        if (className == null || protectionDomain == null) {
            // JVM allows to define a class with "null" name through Unsafe. LambdaMetafactory in Java 8 defines a SAM implementation for method handle this way.
            // ProtectionDomain is unlikely to be null in practice, but checking it doesn't hurt.
            return null;
        }
        try {
            return getLoader(protectionDomain).loadTransformedClass(className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Loader getLoader(ProtectionDomain domain) {
        // This is a very verbose Java 6-compatible way of doing
        // return loaders.computeIfAbsent(domain, this::createLoaderForDomain).
        Loader transformLoader = loaders.get(domain);
        if (transformLoader == null) {
            transformLoader = storeIfAbsent(domain, createLoaderForDomain(domain));
            if (closed) {
                // This replacer was closed while setting up a loader.
                // The transformLoader might be inserted into the loaders map after close(), so let's close it for sure to
                // avoid leaks.
                IoActions.closeQuietly(transformLoader);
                // Throw the exception so the caller doesn't see the obviously closed loader.
                ensureOpened();
            }
        }
        return transformLoader;
    }

    private Loader createLoaderForDomain(ProtectionDomain domain) {
        File originalJarPath = getOriginalFile(domain);
        File transformedJarPath = originalJarPath != null ? classPath.findTransformedJarFor(originalJarPath) : null;
        return transformedJarPath != null ? new JarLoader(originalJarPath, transformedJarPath) : SKIP_INSTRUMENTATION;
    }

    private Loader storeIfAbsent(ProtectionDomain domain, Loader newLoader) {
        Loader oldLoader = loaders.putIfAbsent(domain, newLoader);
        if (oldLoader != null) {
            // Discard the new loader, someone beat us with storing it.
            IoActions.closeQuietly(newLoader);
            return oldLoader;
        }
        return newLoader;
    }

    @Override
    public void close() {
        if (closed) {
            // Already closed.
            return;
        }
        closed = true;
        for (Loader value : loaders.values()) {
            IoActions.closeQuietly(value);
        }
    }

    private void ensureOpened() {
        if (closed) {
            throw new IllegalStateException("Cannot load the transformed class, the replacer is closed");
        }
    }

    @Nullable
    private static File getOriginalFile(ProtectionDomain protectionDomain) {
        // CodeSource is null for dynamically defined classes, or if the ClassLoader doesn't set them properly.
        CodeSource cs = protectionDomain.getCodeSource();
        URL originalUrl = cs != null ? cs.getLocation() : null;
        if (originalUrl == null || !"file".equals(originalUrl.getProtocol())) {
            // Cannot transform classes from anything but files
            return null;
        }
        try {
            return new File(originalUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse file URL " + originalUrl, e);
        }
    }

    private static class Loader implements Closeable {
        @Nullable
        public byte[] loadTransformedClass(String className) throws IOException {
            return null;
        }

        @Override
        public void close() {}
    }

    private class JarLoader extends Loader {
        private final File originalJarFilePath;
        private final File jarFilePath;
        private @Nullable JarCompat jarFile;

        public JarLoader(File originalJarFilePath, File transformedJarFile) {
            this.originalJarFilePath = originalJarFilePath;
            this.jarFilePath = transformedJarFile;
        }

        @Override
        @Nullable
        public synchronized byte[] loadTransformedClass(String className) throws IOException {
            JarFile jarFile = getJarFileLocked();
            // From this point it is safe to load the bytes even if somebody attempts to close the replacer.
            // The close() on this loader will block until this method completes.
            JarEntry classEntry = jarFile.getJarEntry(classNameToPath(className));
            if (classEntry == null) {
                // This can happen if the class was "injected" into the classloader, e.g. when decorated class is generated by the ObjectFactory.
                // Injected classes reuse the protection domain. See ClassLoaderUtils.define and defineDecorator.
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
            // Not calling getJarFileLocked intentionally, to avoid opening the JAR if it isn't opened yet.
            IoActions.closeQuietly(jarFile);
        }

        private JarFile getJarFileLocked() throws IOException {
            ensureOpened();
            if (jarFile == null) {
                jarFile = JarCompat.open(jarFilePath);
                if (jarFile.isMultiRelease() && !isTransformed(jarFile.getJarFile())) {
                    throw new GradleException(
                        "Cannot load multi-release JAR '"
                            + originalJarFilePath.getAbsolutePath()
                            + "' because it cannot be fully instrumented by this version of Gradle.");
                }
            }
            return jarFile.getJarFile();
        }

        private String classNameToPath(String className) {
            return className + ".class";
        }
    }

    private static boolean isTransformed(JarFile jarFile) throws IOException {
        JarEntry entry = jarFile.getJarEntry(MARKER_RESOURCE_NAME);
        if (entry != null) {
            InputStream in = jarFile.getInputStream(entry);
            try {
                return "true".equals(StreamByteBuffer.of(in).readAsString("UTF-8"));
            } finally {
                in.close();
            }
        }
        return false;
    }
}
