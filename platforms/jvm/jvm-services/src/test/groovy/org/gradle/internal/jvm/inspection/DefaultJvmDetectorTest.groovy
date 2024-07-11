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

package org.gradle.internal.jvm.inspection

import org.gradle.api.GradleException
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * Tests {@link DefaultJvmDetector}.
 */
class DefaultJvmDetectorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(DefaultJvmDetectorTest)
    DefaultJvmDetector detector = new DefaultJvmDetector(
        new DefaultJvmMetadataDetector(
            TestFiles.execHandleFactory(),
            TestFiles.tmpDirTemporaryFileProvider(tmpDir.testDirectory)
        )
    )

    def "can get JVM instance for current jvm"() {
        expect:
        detector.detectJvm(Jvm.current().javaHome) is Jvm.current()
        detector.tryDetectJvm(Jvm.current().javaHome) is Jvm.current()
    }

    def "can get JVM instance for java.home dir"() {
        expect:
        detector.detectJvm(new File(System.getProperty("java.home"))) is Jvm.current()
        detector.tryDetectJvm(new File(System.getProperty("java.home"))) is Jvm.current()
    }

    def "fails for non existent java home"() {
        when:
        def file = tmpDir.file("unknown")
        detector.detectJvm(file)

        then:
        def e = thrown(GradleException)
        e.message == "JVM at path '${file}' does not exist."

        when:
        def detected = detector.tryDetectJvm(file)

        then:
        detected == null
    }

    def "fails for invalid jvm"() {
        given:
        def cause = new NullPointerException("cause")
        def metadataDetector = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> {
                return JvmInstallationMetadata.failure(new File("invalid"), cause)
            }
        }
        def detector = new DefaultJvmDetector(metadataDetector)


        when:
        def file = tmpDir.file("whatever").touch()
        detector.detectJvm(file)

        then:
        def e = thrown(GradleException)
        e.message == "JVM at path '${file}' is invalid."
        e.cause == cause

        when:
        def detected = detector.tryDetectJvm(file)

        then:
        detected == null
    }

}
