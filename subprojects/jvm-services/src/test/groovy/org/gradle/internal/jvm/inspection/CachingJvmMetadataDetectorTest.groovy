/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm.inspection

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class CachingJvmMetadataDetectorTest extends Specification {

    @TempDir
    File temporaryFolder

    def "returned metadata from delegate"() {
        def metadata = Mock(JvmInstallationMetadata)
        given:
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> metadata
        }

        def detector = new CachingJvmMetadataDetector(delegate)

        when:
        def actual = detector.getMetadata(testLocation("jdk"))

        then:
        actual.is(metadata)
    }

    def "caches metadata by home"() {
        given:
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> Mock(JvmInstallationMetadata)
        }

        def detector = new CachingJvmMetadataDetector(delegate)

        when:
        def metadata1 = detector.getMetadata(testLocation("jdk"))
        def metadata2 = detector.getMetadata(testLocation("jdk"))

        then:
        metadata1.is(metadata2)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "cached probe are not affected by symlink changes"() {
        given:
        NativeServicesTestFixture.initialize()
        def metaDataDetector = new DefaultJvmMetadataDetector(
            TestFiles.execHandleFactory(),
            TestFiles.tmpDirTemporaryFileProvider(temporaryFolder)
        )
        def detector = new CachingJvmMetadataDetector(metaDataDetector)
        File javaHome1 = Jvm.current().javaHome
        def link = new TestFile(Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(), "jdklink")
        link.createLink(javaHome1)

        when:
        def metadata1 = detector.getMetadata(testLocation(link.absolutePath))
        link.createLink(new File("doesntExist"))
        def metadata2 = detector.getMetadata(testLocation(link.absolutePath))

        then:
        metadata1.javaHome.toString().contains(Jvm.current().javaHome.canonicalPath)
        metadata2.errorMessage.contains("No such directory")
    }


    def "invalidation takes predicate into account"() {
        def location1 = testLocation("jdk1")
        def location2 = testLocation("jdk2")
        def metadata1 = Mock(JvmInstallationMetadata)
        def metadata2 = Mock(JvmInstallationMetadata)
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(location1) >> metadata1
            getMetadata(location2) >> metadata2
        }
        def metadataDetector = new CachingJvmMetadataDetector(delegate)
        metadataDetector.getMetadata(location1)
        metadataDetector.getMetadata(location2)

        when: "cache gets invalidated by predicate, and some calls are made that match it and some that don't"
        metadataDetector.invalidateItemsMatching(it -> it == metadata1)
        metadataDetector.getMetadata(location1)
        metadataDetector.getMetadata(location2)
        then: "only the calls that don't match the predicate get executed again"
        1 * delegate.getMetadata(location1)
        0 * delegate.getMetadata(location2)
    }

    private InstallationLocation testLocation(String filePath) {
        return new InstallationLocation(new File(filePath), "test")
    }
}
