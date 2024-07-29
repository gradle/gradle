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

package org.gradle.launcher.daemon

import groovy.test.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem

class DaemonToolchainInvalidCriteriaIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture {

    def "Given empty daemon-jvm properties file When execute any task Then succeeds using the current java home"() {
        given:
        daemonJvmPropertiesFile.touch()
        captureJavaHome()

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
    }

    def "Given non-integer toolchain version When execute any task Then fails with expected exception message"() {
        given:
        daemonJvmPropertiesFile.writeProperties((DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "stringVersion")
        when:
        fails 'help'
        then:
        failure.assertHasDescription("Value 'stringVersion' given for toolchainVersion is an invalid Java version")
    }

    def "Given negative toolchain version When execute any task Then fails with expected exception message"() {
        given:
        daemonJvmPropertiesFile.writeProperties((DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "-1")
        when:
        fails 'help'
        then:
        failure.assertHasDescription("Value '-1' given for toolchainVersion is an invalid Java version")
    }

    def "Given unexpected toolchain vendor When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria(JavaVersion.VERSION_17, "unexpectedVendor")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: Compatible with Java 17, vendor matching('unexpectedVendor') (from gradle/gradle-daemon-jvm.properties).")
    }

    @NotYetImplemented
    def "Given unexpected toolchain implementation When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria(JavaVersion.VERSION_17, "amazon", "unknownImplementation")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option toolchainImplementation doesn't accept value 'unknownImplementation'. Possible values are [VENDOR_SPECIFIC, J9]")
    }
}
