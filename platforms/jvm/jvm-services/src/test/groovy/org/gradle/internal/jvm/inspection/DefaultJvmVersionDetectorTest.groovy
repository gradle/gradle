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
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.process.internal.ExecException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultJvmVersionDetectorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(DefaultJvmVersionDetectorTest)
    DefaultJvmVersionDetector detector = new DefaultJvmVersionDetector(
        new DefaultJvmMetadataDetector(
            TestFiles.execHandleFactory(),
            TestFiles.tmpDirTemporaryFileProvider(tmpDir.testDirectory)
        )
    )

    def "can determine version of current jvm"() {
        expect:
        detector.getJavaVersionMajor(Jvm.current()) == JavaVersion.current().majorVersionNumber
    }

    def "can determine version of java command for current jvm"() {
        expect:
        detector.getJavaVersionMajor(Jvm.current().getJavaExecutable().path) == JavaVersion.current().majorVersionNumber
    }

    def "can determine version of java command without file extension"() {
        expect:
        detector.getJavaVersionMajor(Jvm.current().getJavaExecutable().path.replace(".exe", "")) == JavaVersion.current().majorVersionNumber
    }

    def "fails for unknown java command"() {
        when:
        detector.getJavaVersionMajor("unknown")

        then:
        def e = thrown(ExecException)
        e.message.contains("A problem occurred starting process 'command 'unknown''")
    }

    def "fails for invalid jvm"() {
        given:
        def cause = new NullPointerException("cause");
        def metadataDetector = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> {
                return JvmInstallationMetadata.failure(new File("invalid"), cause)
            }
        }
        def detector = new DefaultJvmVersionDetector(metadataDetector)

        when:
        detector.getJavaVersionMajor(Jvm.current())

        then:
        def e = thrown(GradleException)
        e.message.contains("Unable to determine version for JDK located")
        e.message.contains("invalid")
        e.cause == cause
    }

}
