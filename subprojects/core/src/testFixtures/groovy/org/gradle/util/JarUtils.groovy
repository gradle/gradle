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

package org.gradle.util


import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.internal.JarUtil

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class JarUtils {
    static def jarWithContents(Map<String, String> contents) {
        def out = new ByteArrayOutputStream()
        def jarOut = new JarOutputStream(out)
        try {
            contents.each { file, fileContents ->
                def zipEntry = new ZipEntry(file)
                zipEntry.setTime(0)
                jarOut.putNextEntry(zipEntry)
                jarOut << fileContents
            }
        } finally {
            jarOut.close()
        }
        return out.toByteArray()
    }

    /**
     * Builds a JAR file with a DSL. The JAR file uses deterministic timestamps. The Jar manifest must be configured explicitly, as a first step.
     * <p>
     * <pre>
     *     jar("path/to/file.jar") {
     *         manifest {  // can be withoutManifest()
     *             mainAttributes.putValue("Multi-Release", "true")
     *         }
     *
     *         entry("Foo.class", fooBytes)
     *         versionedEntry(11, "Foo.class", fooBytesForJava11)
     *     }
     *
     * </pre>
     * @param jarFile the jar file to create
     * @param closure the configuration of the JAR file
     * @return the path to the created JAR file.
     */
    static File jar(File jarFile, @DelegatesTo(value = JarBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        try (def out = jarFile.newOutputStream(); def jarOut = new JarOutputStream(out)) {
            closure.setDelegate(new JarBuilder(jarOut))
            closure()
        }

        jarFile
    }

    /**
     * DSL helper to build the JAR file. Start building with configuring the manifest ({@link JarBuilder#manifest(groovy.lang.Closure)}
     * or specifying the lack thereof ({@link JarBuilder#withoutManifest()}.
     */
    static class JarBuilder {
        /**
         * @see org.gradle.api.internal.file.archive.ZipCopyAction#CONSTANT_TIME_FOR_ZIP_ENTRIES
         */
        private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = LocalDateTime.parse("1980-02-01T00:00:00.00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        private final JarOutputStream jarOut
        private boolean hasConfiguredManifest

        JarBuilder(JarOutputStream jarOut) {
            this.jarOut = jarOut
        }

        /**
         * Specifies that this JAR has no manifest.
         */
        void withoutManifest() {
            assert !hasConfiguredManifest: "Already written the manifest"
            hasConfiguredManifest = true
        }

        /**
         * Configures the JAR manifest.
         */
        void manifest(@DelegatesTo(value = ManifestWithDsl, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
            assert !hasConfiguredManifest: "Already written the manifest"

            def man = new ManifestWithDsl()
            man.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

            closure.setDelegate(man)
            closure()

            jarOut.putNextEntry(newEntryWithFixedTime(JarFile.MANIFEST_NAME))
            man.write(jarOut)

            hasConfiguredManifest = true
            this
        }

        private static JarEntry newEntryWithFixedTime(String path) {
            return new JarEntry(path).tap {
                // setTimeLocal() is better but it is Java 9+.
                setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES)
            }
        }

        /**
         * Writes a versioned flavor of a JAR entry. The {@code path} should be a non-versioned path, e.g. {@code path/to/Foo.class}.
         * @param version the java version (9, 10, etc.), must be &gt;8
         * @param path the path of the entry (non-versioned)
         * @param bytes the body of the entry
         */
        void versionedEntry(int version, String path, byte[] bytes) {
            entry(JarTestFixture.toVersionedPath(version, path), bytes)
        }

        /**
         * Writes a versioned flavor of a JAR entry. The {@code path} should be a non-versioned path, e.g. {@code path/to/Foo.class}.
         * @param version the java version (9, 10, etc.), must be &gt;8
         * @param path the path of the entry (non-versioned)
         * @param body the body of the entry which is encoded with UTF-8
         */
        void versionedEntry(int version, String path, String body) {
            entry(JarTestFixture.toVersionedPath(version, path), body)
        }

        /**
         * Writes a JAR entry.
         * @param path the path of the entry
         * @param bytes the body of the entry
         */
        void entry(String path, byte[] bytes) {
            checkManifestWritten()
            jarOut.putNextEntry(newEntryWithFixedTime(path))
            jarOut.write(bytes)
        }

        /**
         * Writes a JAR entry.
         * @param path the path of the entry
         * @param body the body of the entry which is encoded with UTF-8
         */
        void entry(String path, String body) {
            entry(path, body.getBytes(StandardCharsets.UTF_8))
        }

        private void checkManifestWritten() {
            assert hasConfiguredManifest: "Must have manifest before entries. Use withoutManifest() to skip it explicitly."
        }
    }

    /**
     * A Manifest with additional goodies to make its configuration more pleasant.
     */
    static class ManifestWithDsl extends Manifest {
        /**
         * Configures this Manifest to be Multi-Release.
         */
        void multiRelease() {
            mainAttributes.putValue(JarUtil.MULTI_RELEASE_ATTRIBUTE, "true")
        }
    }
}
