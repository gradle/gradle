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
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.internal.buildconfiguration.fixture.BuildPropertiesFixture
import org.gradle.internal.jvm.Jvm

class DaemonToolchainInvalidCriteriaIntegrationTest extends AbstractIntegrationSpec implements BuildPropertiesFixture {

    def "Given empty build properties file When execute any task Then succeeds using the current java home"() {
        given:
        buildPropertiesFile.touch()
        captureJavaHome()

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
    }

    def "Given non-integer toolchain version When execute any task Then fails with expected exception message"() {
        given:
        buildPropertiesFile.writeProperties((BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "stringVersion")
        when:
        fails 'help'
        then:
        failure.assertHasDescription("Value 'stringVersion' given for daemon.jvm.toolchain.version is an invalid Java version")
    }

    def "Given negative toolchain version When execute any task Then fails with expected exception message"() {
        given:
        buildPropertiesFile.writeProperties((BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "-1")
        when:
        fails 'help'
        then:
        failure.assertHasDescription("Value '-1' given for daemon.jvm.toolchain.version is an invalid Java version")
    }

    @NotYetImplemented
    def "Given unexpected toolchain vendor When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria(JavaVersion.VERSION_17, "unexpectedVendor")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option daemon.jvm.toolchain.vendor doesn't accept value 'unexpectedVendor'. Possible values are " +
            "[ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN]")
    }

    @NotYetImplemented
    def "Given unexpected toolchain implementation When execute any task Then fails with expected exception message"() {
        given:
        writeJvmCriteria(JavaVersion.VERSION_17, "amazon", "unknownImplementation")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option daemon.jvm.toolchain.implementation doesn't accept value 'unknownImplementation'. Possible values are [VENDOR_SPECIFIC, J9]")
    }
}
