/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r88

import groovy.test.NotYetImplemented
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException

@TargetGradleVersion(">=8.8")
class DaemonToolchainInvalidCriteriaCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    def "Given empty daemon-jvm properties file When execute any task Then succeeds using the current java home"() {
        given:
        buildPropertiesFile.touch()
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(currentJavaHome)
    }

    def "Given non-integer toolchain version When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria("stringVersion")

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Value 'stringVersion' given for toolchainVersion is an invalid Java version")
    }

    def "Given negative toolchain version When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria("-1")

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Value '-1' given for toolchainVersion is an invalid Java version")
    }

    @NotYetImplemented
    def "Given unexpected toolchain vendor When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria("17", "unexpectedVendor")

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Option toolchainVendor doesn't accept value 'unexpectedVendor'. Possible values are " +
            "[ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN]")
    }

    @NotYetImplemented
    def "Given unexpected toolchain implementation When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria("17", "amazon", "unknownImplementation")

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Option toolchainImplementation doesn't accept value 'unknownImplementation'. Possible values are [VENDOR_SPECIFIC, J9]")
    }
}
