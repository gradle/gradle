/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.fingerprint.classpath.impl

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryPersistentIndexedCache
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tmpDir")
@UsesNativeServices
class DefaultCompileClasspathFingerprinterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.genericFileTreeSnapshotter(), TestFiles.fileSystem())
    TestInMemoryPersistentIndexedCache<HashCode, HashCode> resourceHashesCache = new TestInMemoryPersistentIndexedCache<>(new HashCodeSerializer())
    def cacheService = new DefaultResourceSnapshotterCacheService(resourceHashesCache)
    def fingerprinter = new DefaultCompileClasspathFingerprinter(cacheService, fileCollectionSnapshotter, stringInterner)

    def "gradle api jars have the same hash"() {
        def currentGradleApiJar = new File(new File(System.getProperty("user.home")), ".gradle/caches/7.1-20210328220041+0000/generated-gradle-jars/gradle-api-7.1-20210328220041+0000.jar")

        def apiJarFingerprint = fingerprinter.fingerprint(files(currentGradleApiJar))
        println("Api JAR compile fingerprint hash: ${apiJarFingerprint.hash}")
        expect:
        currentGradleApiJar.file
        apiJarFingerprint.hash == HashCode.fromString('39ae4d759967d412393a681e07dcb862')
    }

    private static FileCollection files(File... files) {
        return TestFiles.fixed(files)
    }
}
