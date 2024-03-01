/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.JavaVersion;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

/**
 * Compatibility wrapper for multi-release JARs that can be used on Java 8 too.
 */
abstract class JarCompat implements Closeable {
    public static final boolean JAVA_9_COMPATIBLE = JavaVersion.current().isJava9Compatible();

    protected final JarFile jarFile;

    private JarCompat(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    public final JarFile getJarFile() {
        return jarFile;
    }

    public abstract boolean isMultiRelease();

    public abstract int javaRuntimeVersionUsed();

    @Override
    public final void close() throws IOException {
        jarFile.close();
    }

    public static JarCompat open(File jarFile) throws IOException {
        if (JAVA_9_COMPATIBLE) {
            return new MultiReleaseSupportingJar(jarFile);
        }
        // Running on Java 8, fall back to the old ways.
        return new LegacyJar(jarFile);
    }


    private static class LegacyJar extends JarCompat {
        public LegacyJar(File jarFile) throws IOException {
            super(new JarFile(jarFile));
        }

        @Override
        public boolean isMultiRelease() {
            // Pre-Java 9 platforms do not support multi-release JARs.
            return false;
        }

        @Override
        public int javaRuntimeVersionUsed() {
            throw new UnsupportedOperationException("Cannot get runtime version used when running on Java <9");
        }
    }

    @SuppressWarnings("Since15")
    private static class MultiReleaseSupportingJar extends JarCompat {
        MultiReleaseSupportingJar(File jarFile) throws IOException {
            // Set up the MR-JAR properly when running on Java 9+.
            super(new JarFile(jarFile, true, JarFile.OPEN_READ, JarFile.runtimeVersion()));
        }

        @Override
        public boolean isMultiRelease() {
            return jarFile.isMultiRelease();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int javaRuntimeVersionUsed() {
            // Deprecated major() is used to keep Java 9 compatibility.
            return JarFile.runtimeVersion().major();
        }
    }
}
