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

package org.gradle.internal.classloader

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.internal.classpath.TransformedClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import java.security.CodeSigner
import java.security.CodeSource
import java.security.ProtectionDomain

import static org.gradle.util.JarUtils.jar

class TransformReplacerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    private static final byte[] ORIGINAL_CLASS = new byte[]{1}
    private static final byte[] ORIGINAL_VERSIONED_CLASS = new byte[]{10}
    private static final byte[] INSTRUMENTED_CLASS = new byte[]{2}
    private static final byte[] INSTRUMENTED_VERSIONED_CLASS = new byte[]{20}

    def "replaces original class with transformed in non-multi-release JAR"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {}

            entry("Foo.class", ORIGINAL_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {}

            entry("Foo.class", INSTRUMENTED_CLASS)
        }

        TransformedClassPath cp = classPath((original): transformed)

        expect:
        INSTRUMENTED_CLASS == loadTransformedClass(cp, "Foo", original)
    }

    def "replaces original class with transformed in JAR without manifest"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {}

            entry("Foo.class", ORIGINAL_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            withoutManifest()

            entry("Foo.class", INSTRUMENTED_CLASS)
        }

        TransformedClassPath cp = classPath((original): transformed)

        expect:
        INSTRUMENTED_CLASS == loadTransformedClass(cp, "Foo", original)
    }

    def "replaces original class with transformed in multi-release JAR"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", ORIGINAL_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", INSTRUMENTED_CLASS)
            entry(TransformReplacer.MARKER_RESOURCE_NAME, "true")
        }

        TransformedClassPath cp = classPath((original): transformed)

        expect:
        INSTRUMENTED_CLASS == loadTransformedClass(cp, "Foo", original)
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "replaces original class with transformed from versioned directory in multi-release JAR"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", ORIGINAL_CLASS)
            versionedEntry(currentJvmMajor, "Foo.class", ORIGINAL_VERSIONED_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", INSTRUMENTED_CLASS)
            entry(TransformReplacer.MARKER_RESOURCE_NAME, "true")

            versionedEntry(currentJvmMajor, "Foo.class", INSTRUMENTED_VERSIONED_CLASS)
        }

        TransformedClassPath cp = classPath((original): transformed)

        expect:
        INSTRUMENTED_VERSIONED_CLASS == loadTransformedClass(cp, "Foo", original)
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "replaces original class with transformed from versioned directory in multi-release JAR if next version is not supported"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", ORIGINAL_CLASS)
            versionedEntry(currentJvmMajor, "Foo.class", ORIGINAL_VERSIONED_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", INSTRUMENTED_CLASS)
            entry(TransformReplacer.MARKER_RESOURCE_NAME, "true")

            versionedEntry(currentJvmMajor, "Foo.class", INSTRUMENTED_VERSIONED_CLASS)
            versionedEntry(currentJvmMajor + 1, TransformReplacer.MARKER_RESOURCE_NAME, "false")
        }

        TransformedClassPath cp = classPath((original): transformed)

        expect:
        INSTRUMENTED_VERSIONED_CLASS == loadTransformedClass(cp, "Foo", original)
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "fails loading if transformed multi-release jar has no marker resource"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", ORIGINAL_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", INSTRUMENTED_CLASS)
        }

        TransformedClassPath cp = classPath((original): transformed)

        when:
        loadTransformedClass(cp, "Foo", original)

        then:
        thrown(GradleException)
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "fails loading if transformed multi-release jar does not instrument current JVM"() {
        given:
        def original = jar(testDir.file("original.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", ORIGINAL_CLASS)
            versionedEntry(currentJvmMajor, "Foo.class", ORIGINAL_VERSIONED_CLASS)
        }

        def transformed = jar(testDir.file("transformed.jar")) {
            manifest {
                multiRelease()
            }

            entry("Foo.class", INSTRUMENTED_CLASS)
            entry(TransformReplacer.MARKER_RESOURCE_NAME, "true")

            versionedEntry(currentJvmMajor, TransformReplacer.MARKER_RESOURCE_NAME, "false")
        }

        TransformedClassPath cp = classPath((original): transformed)

        when:
        loadTransformedClass(cp, "Foo", original)

        then:
        thrown(GradleException)
    }

    private static final byte[] loadTransformedClass(TransformedClassPath cp, String className, File originalJar) {
        try (TransformReplacer replacer = new TransformReplacer(cp)) {
            return replacer.getInstrumentedClass(className, protectionDomain(originalJar))
        }
    }

    private static TransformedClassPath classPath(Map<File, File> originalToTransformed) {
        def builder = TransformedClassPath.builderWithExactSize(originalToTransformed.size())
        originalToTransformed.forEach(builder::add)
        return builder.build()
    }

    private static int getCurrentJvmMajor() {
        return JavaVersion.current().majorVersion.toInteger()
    }

    private static ProtectionDomain protectionDomain(File jarFile) {
        def cs = new CodeSource(jarFile.toURI().toURL(), null as CodeSigner[])
        return new ProtectionDomain(cs, null)
    }
}
