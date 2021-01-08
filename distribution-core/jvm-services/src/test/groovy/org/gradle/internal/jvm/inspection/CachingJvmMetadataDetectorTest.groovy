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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class CachingJvmMetadataDetectorTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder

    def "returned metadata from delegate"() {
        def metadata = Mock(JvmInstallationMetadata)
        given:
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as File) >> metadata
        }

        def detector = new CachingJvmMetadataDetector(delegate)

        when:
        def actual = detector.getMetadata(new File("jdk"))

        then:
        actual.is(metadata)
    }

    def "caches metadata by home"() {
        given:
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as File) >> Mock(JvmInstallationMetadata)
        }

        def detector = new CachingJvmMetadataDetector(delegate)

        when:
        def metadata1 = detector.getMetadata(new File("jdk"))
        def metadata2 = detector.getMetadata(new File("jdk"))

        then:
        metadata1.is(metadata2)
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "cached probe are not affected by symlink changes"() {
        given:
        NativeServicesTestFixture.initialize()
        def metaDataDetector = new DefaultJvmMetadataDetector(
            TestFiles.execHandleFactory(),
            TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.root)
        )
        def detector = new CachingJvmMetadataDetector(metaDataDetector)
        File javaHome1 = Jvm.current().javaHome
        def link = new TestFile(temporaryFolder.newFolder(), "jdklink")
        link.createLink(javaHome1)

        when:
        def metadata1 = detector.getMetadata(link)
        link.createLink(new File("doesntExist"))
        def metadata2 = detector.getMetadata(link)

        then:
        metadata1.javaHome.toString().contains(Jvm.current().javaHome.canonicalPath)
        metadata2.errorMessage.contains("No such directory")
    }
}
