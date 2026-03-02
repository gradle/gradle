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

import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.internal.ToBeImplemented

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

    @ToBeImplemented
    @TargetGradleVersion(">=8.13")
    def "Given unexpected toolchain vendor When execute any task Then fails with expected exception message"() {
        given:
        def properties = new Properties()
        properties.put("toolchainVersion", "17")
        properties.put("toolchainVendor", "unexpectedVendor")
        buildPropertiesFile.writeProperties(properties)
        requireDaemons()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
        e.cause.message.contains("vendor=vendor matching('unexpectedVendor')")
    }

    @ToBeImplemented
    @TargetGradleVersion(">=8.13")
    def "Given unexpected toolchain implementation When execute any task Then fails with expected exception message"() {
        given:
        def properties = new Properties()
        properties.put("toolchainVersion", "17")
        properties.put("toolchainVendor", "amazon")
        properties.put("toolchainImplementation", "unknownImplementation")
        buildPropertiesFile.writeProperties(properties)
        requireDaemons()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
        e.cause.message.contains("implementation=vendor-specific")
    }
}
