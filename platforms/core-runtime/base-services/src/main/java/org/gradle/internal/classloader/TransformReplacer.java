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
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.model.internal.asm.AsmConstants;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
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
        File originalPath = getOriginalFile(domain);
        File transformedPath = originalPath != null ? classPath.findTransformedEntryFor(originalPath) : null;
        if (transformedPath == null) {
            return SKIP_INSTRUMENTATION;
        } else if (transformedPath.isFile()) {
            return new JarLoader(originalPath, transformedPath);
        } else if (transformedPath.isDirectory()) {
            return new DirectoryLoader(transformedPath);
        }
        throw new IllegalArgumentException("Cannot load transformed entry " + transformedPath.getAbsolutePath());
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
        private final File originalPath;
        private final File transformedJarPath;
        private @Nullable JarCompat jarFile;

        public JarLoader(File originalPath, File transformedJarPath) {
            this.originalPath = originalPath;
            this.transformedJarPath = transformedJarPath;
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
                jarFile = JarCompat.open(transformedJarPath);
                if (jarFile.isMultiRelease() && !isTransformed(jarFile.getJarFile())) {
                    throw new GradleException(String.format(
                        "Cannot load multi-release JAR '%s' because it cannot be fully instrumented for Java %d by this version of Gradle. Please use a supported Java version (<=%d).",
                        originalPath.getAbsolutePath(),
                        jarFile.javaRuntimeVersionUsed(),
                        AsmConstants.MAX_SUPPORTED_JAVA_VERSION));
                }
            }
            return jarFile.getJarFile();
        }
    }

    private static boolean isTransformed(JarFile jarFile) throws IOException {
        JarEntry entry = jarFile.getJarEntry(MarkerResource.RESOURCE_NAME);
        if (entry != null) {
            InputStream in = jarFile.getInputStream(entry);
            try {
                return MarkerResource.TRANSFORMED.equals(MarkerResource.readFromStream(in));
            } finally {
                in.close();
            }
        }
        return false;
    }

    private static class DirectoryLoader extends Loader {
        private final File transformedPath;

        public DirectoryLoader(File transformedDirPath) {
            this.transformedPath = transformedDirPath;
        }

        @Nullable
        @Override
        public byte[] loadTransformedClass(String className) throws IOException {
            File classFile = new File(transformedPath, classNameToPath(className));
            if (!classFile.exists()) {
                // This can happen if the class was "injected" into the classloader, e.g. when decorated class is generated by the ObjectFactory.
                // Injected classes reuse the protection domain. See ClassLoaderUtils.define and defineDecorator.
                // TODO(mlopatkin) we don't do any kind of integrity checks for the directory. If the user deletes transformed classes but not the receipt
                //  file, we're going to use originals silently.
                return null;
            }
            InputStream classBytes = new FileInputStream(classFile);
            try {
                return StreamByteBuffer.of(classBytes).readAsByteArray();
            } finally {
                classBytes.close();
            }
        }
    }

    private static String classNameToPath(String className) {
        return className + ".class";
    }

    /**
     * Transformed Multi-Release JARs intended for loading with the TransformReplacer must contain a special resource file named {@code RESOURCE_NAME} and with the body {@code TRANSFORMED.asBytes()}.
     * If some versioned directories of the JAR haven't been processed, then these directories must contain presiding (overriding) resource with the same name but with
     * {@code NOT_TRANSFORMED.asBytes()} as body.
     * <p>
     * TransformReplacer throws upon opening a JAR file if the current JVM loads the NOT_TRANSFORMED marker resource from the JAR.
     * TransformReplacer throws upon opening a multi-release JAR without the marker resource.
     */
    public enum MarkerResource {
        // The transformed marker resource is an empty file to reduce archive size in the most common case.
        TRANSFORMED(new byte[0]),
        // Not transformed marker resource is a 1-byte file with a single "N" symbol.
        NOT_TRANSFORMED(new byte[]{'N'});

        public static final String RESOURCE_NAME = TransformReplacer.class.getName() + ".transformed";

        @SuppressWarnings("ImmutableEnumChecker")
        private final byte[] markerBody;

        MarkerResource(byte[] markerBody) {
            this.markerBody = markerBody;
        }

        /**
         * Reads the contents of the MarkerResource and returns the appropriate constant.
         *
         * @param in the stream to read from
         * @return the corresponding marker resource
         * @throws IOException if reading fails
         */
        public static MarkerResource readFromStream(InputStream in) throws IOException {
            int readByte = in.read();
            if (readByte < 0) {
                return TRANSFORMED;
            }
            // Be lenient - any non-empty file means the JAR isn't transformed.
            return NOT_TRANSFORMED;
        }

        public byte[] asBytes() {
            return markerBody;
        }
    }
}
