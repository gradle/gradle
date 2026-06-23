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

package org.gradle.api.internal.changedetection.state

import org.apache.commons.io.IOUtils
import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext
import org.gradle.internal.fingerprint.hashing.ZipEntryContext
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.tools.api.ApiClassExtractor
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

class AbiExtractingClasspathResourceHasherTest extends Specification {
    @Rule
    TemporaryFolder temporaryDirectory = new TemporaryFolder()

    def setup() {
        temporaryDirectory.create()
    }

    @Issue("https://github.com/gradle/gradle/issues/20398")
    def "falls back to full file hash when abi extraction fails for a regular file"() {
        def apiClassExtractor = Mock(ApiClassExtractor)

        def resourceHasher = AbiExtractingClasspathResourceHasher.withFallback(apiClassExtractor)
        def fileSnapshotContext = Mock(RegularFileSnapshotContext)
        def fileSnapshot = Mock(RegularFileSnapshot)

        given:
        def file = temporaryDirectory.newFile('String.class')
        file.bytes = bytesOf(String.class)

        when:
        resourceHasher.hash(fileSnapshotContext)

        then:
        1 * fileSnapshotContext.getSnapshot() >> fileSnapshot
        2 * fileSnapshot.getName() >> file.name
        1 * fileSnapshot.getAbsolutePath() >> file.absolutePath
        1 * apiClassExtractor.extractApiClassFrom(_) >> { args -> throw new Exception("Boom!") }

        and:
        1 * fileSnapshot.getHash()

        and:
        noExceptionThrown()
    }

    @Issue("https://github.com/gradle/gradle/issues/20398")
    def "falls back to full zip entry hash when abi extraction fails for a zip entry"() {
        def apiClassExtractor = Mock(ApiClassExtractor)

        def resourceHasher = AbiExtractingClasspathResourceHasher.withFallback(apiClassExtractor)
        def zipEntryContext = Mock(ZipEntryContext)
        def zipEntry = Mock(ZipEntry)
        def classContent = bytesOf(String.class)

        when:
        def hash = resourceHasher.hash(zipEntryContext)

        then:
        1 * zipEntryContext.getEntry() >> zipEntry
        2 * zipEntry.getName() >> 'String.class'
        1 * zipEntry.getContent() >> classContent
        1 * apiClassExtractor.extractApiClassFrom(_) >> { args -> throw new Exception("Boom!") }

        and:
        hash == Hashing.hashBytes(classContent)

        and:
        noExceptionThrown()
    }

    def "does not fall back to full file hash when fallback is not requested and abi extraction fails for a regular file"() {
        def apiClassExtractor = Mock(ApiClassExtractor)

        def resourceHasher = AbiExtractingClasspathResourceHasher.withoutFallback(apiClassExtractor)
        def fileSnapshotContext = Mock(RegularFileSnapshotContext)
        def fileSnapshot = Mock(RegularFileSnapshot)

        given:
        def file = temporaryDirectory.newFile('String.class')
        file.bytes = bytesOf(String.class)

        when:
        resourceHasher.hash(fileSnapshotContext)

        then:
        1 * fileSnapshotContext.getSnapshot() >> fileSnapshot
        1 * fileSnapshot.getName() >> file.name
        1 * fileSnapshot.getAbsolutePath() >> file.absolutePath
        1 * apiClassExtractor.extractApiClassFrom(_) >> { args -> throw new Exception("Boom!") }

        and:
        def e = thrown(Exception)
        e.message == "Boom!"
    }

    def "does not fall back to full zip entry hashing when fallback is not requested and abi extraction fails for a zip entry"() {
        def apiClassExtractor = Mock(ApiClassExtractor)

        def resourceHasher = AbiExtractingClasspathResourceHasher.withoutFallback(apiClassExtractor)
        def zipEntryContext = Mock(ZipEntryContext)
        def zipEntry = Mock(ZipEntry)

        when:
        resourceHasher.hash(zipEntryContext)

        then:
        1 * zipEntryContext.getEntry() >> zipEntry
        2 * zipEntry.getName() >> 'String.class'
        1 * zipEntry.getContent() >> bytesOf(String.class)
        1 * apiClassExtractor.extractApiClassFrom(_) >> { args -> throw new Exception("Boom!") }

        and:
        def e = thrown(Exception)
        e.message == "Boom!"
    }

    private static byte[] bytesOf(Class<?> clazz) {
        String classFile = "/${clazz.getName().replaceAll('\\.', '/')}.class"
        return IOUtils.toByteArray(clazz.getResource(classFile).openStream())
    }
}
