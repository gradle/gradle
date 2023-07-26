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

package org.gradle.internal.classpath.fingerprint


import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.classanalysis.AsmConstants
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryIndexedCache
import org.junit.Rule
import spock.lang.Specification

import java.util.jar.Manifest

import static org.gradle.util.JarUtils.jar

class InstrumentedClasspathFingerprinterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.fileSystem())
    TestInMemoryIndexedCache<HashCode, HashCode> resourceHashesCache = new TestInMemoryIndexedCache<>(new HashCodeSerializer())
    def cacheService = new DefaultResourceSnapshotterCacheService(resourceHashesCache)

    private static final int FIRST_SUPPORTED_VERSION = 8
    private static final int SOME_SUPPORTED_VERSION = AsmConstants.MAX_SUPPORTED_JAVA_VERSION - 1
    private static final int LATEST_SUPPORTED_VERSION = AsmConstants.MAX_SUPPORTED_JAVA_VERSION
    private static final int FIRST_UNSUPPORTED_VERSION = AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1
    private static final int NEXT_UNSUPPORTED_VERSION = AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 2

    def "can fingerprint jar with manifest"() {
        def jarFile = jar(testDir.file("boring.jar")) {
            manifest {}

            classEntry(Dummy)
        }

        expect:
        fingerprint(jarFile) != null
    }

    def "can fingerprint jar without manifest"() {
        def jarFile = jar(testDir.file("noManifest.jar")) {
            withoutManifest()

            classEntry(Dummy)
        }

        expect:
        fingerprint(jarFile) != null
    }

    def "multi-release jar without unsupported versioned directories has same fingerprint on all jvms"() {
        def jarFile = jar(testDir.file("mrjar.jar")) {
            manifest(multiRelease())

            classEntry(Dummy)
            versionedClassEntry(supportedVersionInJar, Dummy)
        }

        when:
        def fingerprintOnSupportedJvm = fingerprintOnJvm(jarFile, FIRST_SUPPORTED_VERSION)

        then:
        verifyAll {
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, SOME_SUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, LATEST_SUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, FIRST_UNSUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, NEXT_UNSUPPORTED_VERSION).hash
        }

        where:
        supportedVersionInJar << [SOME_SUPPORTED_VERSION, LATEST_SUPPORTED_VERSION]
    }

    def "non multi-release jar with unsupported versioned directories has same fingerprint on all jvms"() {
        def jarFile = jar(testDir.file("mrjar.jar")) {
            manifest {}

            classEntry(Dummy)
            versionedClassEntry(LATEST_SUPPORTED_VERSION, Dummy)
            versionedClassEntry(FIRST_UNSUPPORTED_VERSION, Dummy)
        }

        when:
        def fingerprintOnSupportedJvm = fingerprintOnJvm(jarFile, FIRST_SUPPORTED_VERSION)

        then:
        verifyAll {
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, SOME_SUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, LATEST_SUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, FIRST_UNSUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, NEXT_UNSUPPORTED_VERSION).hash
        }
    }

    def "multi-release jar with unsupported versioned directory has same fingerprint on supported jvm"() {
        def jarFile = jar(testDir.file("mrjar.jar")) {
            manifest(multiRelease())

            classEntry(Dummy)
            versionedClassEntry(FIRST_UNSUPPORTED_VERSION, Dummy)
            versionedClassEntry(NEXT_UNSUPPORTED_VERSION, Dummy)
        }

        when:
        def fingerprintOnSupportedJvm = fingerprintOnJvm(jarFile, FIRST_SUPPORTED_VERSION)

        then:
        verifyAll {
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, SOME_SUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, LATEST_SUPPORTED_VERSION).hash
        }
    }

    def "multi-release jar with unsupported versioned directory has same fingerprint on unsupported jvms not loading from this directory"() {
        def jarFile = jar(testDir.file("mrjar.jar")) {
            manifest(multiRelease())

            classEntry(Dummy)
            versionedClassEntry(NEXT_UNSUPPORTED_VERSION, Dummy)
        }

        when:
        def fingerprintOnSupportedJvm = fingerprintOnJvm(jarFile, FIRST_SUPPORTED_VERSION)

        then:
        fingerprintOnSupportedJvm.hash == fingerprintOnJvm(jarFile, FIRST_UNSUPPORTED_VERSION).hash
    }

    def "multi-release jar with unsupported versioned directory has different fingerprint on jvms loading from it"() {
        def jarFile = jar(testDir.file("mrjar.jar")) {
            manifest(multiRelease())

            classEntry(Dummy)
            versionedClassEntry(FIRST_UNSUPPORTED_VERSION, Dummy)
        }

        when:
        def fingerprintOnSupportedJvm = fingerprintOnJvm(jarFile, FIRST_SUPPORTED_VERSION)

        then:
        verifyAll {
            fingerprintOnSupportedJvm.hash != fingerprintOnJvm(jarFile, FIRST_UNSUPPORTED_VERSION).hash
            fingerprintOnSupportedJvm.hash != fingerprintOnJvm(jarFile, NEXT_UNSUPPORTED_VERSION).hash
        }
    }

    private CurrentFileCollectionFingerprint fingerprint(File jarFile) {
        return new InstrumentedClasspathFingerprinter(cacheService, fileCollectionSnapshotter, stringInterner).fingerprint(fileSystemAccess.read(jarFile.absolutePath), null)
    }

    private CurrentFileCollectionFingerprint fingerprintOnJvm(File jarFile, int jvmMajor) {
        return new InstrumentedClasspathFingerprinter(jvmMajor, cacheService, fileCollectionSnapshotter, stringInterner).fingerprint(fileSystemAccess.read(jarFile.absolutePath), null)
    }

    private static Closure<?> multiRelease() {
        return {
            (delegate as Manifest).mainAttributes.putValue("Multi-Release", "true")
        }
    }

    private static class Dummy {}
}
