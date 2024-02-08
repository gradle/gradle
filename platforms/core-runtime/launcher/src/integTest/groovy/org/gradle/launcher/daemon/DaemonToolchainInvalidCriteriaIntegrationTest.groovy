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

import org.gradle.integtests.fixtures.daemon.DaemonToolchainIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class DaemonToolchainInvalidCriteriaIntegrationTest extends DaemonToolchainIntegrationSpec {

    def "Given empty build properties file When execute any task Then succeeds using the current java home"() {
        def currentJvm = Jvm.current()

        given:
        createDaemonJvmToolchainCriteria()

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm, false)
    }

    def "Given non-integer toolchain version When execute any task Then fails with expected exception message"() {
        given:
        createDaemonJvmToolchainCriteria("stringVersion")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Value 'stringVersion' given for daemon.jvm.toolchain.version Build property is invalid (the value should be an int)")
    }

    def "Given negative toolchain version When execute any task Then fails with expected exception message"() {
        given:
        createDaemonJvmToolchainCriteria("-1")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Value '-1' given for daemon.jvm.toolchain.version Build property is invalid (the value should be a positive int)")
    }

    def "Given undefined toolchain version but valid vendor and implementation When execute any task Then fails with expected exception message"() {
        given:
        createDaemonJvmToolchainCriteria(null, "ibm", "j9")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option daemon.jvm.toolchain.version undefined on Build properties. Execute 'updateDaemonJvm' task with desired criteria to fix it.")
    }

    def "Given unexpected toolchain vendor When execute any task Then fails with expected exception message"() {
        given:
        createDaemonJvmToolchainCriteria("17", "unexpectedVendor")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option daemon.jvm.toolchain.vendor doesn't accept value 'unexpectedVendor'. Possible values are " +
            "[ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN]")
    }

    def "Given unexpected toolchain implementation When execute any task Then fails with expected exception message"() {
        given:
        createDaemonJvmToolchainCriteria("17", "amazon", "unknownImplementation")

        when:
        fails 'help'

        then:
        failureDescriptionContains("Option daemon.jvm.toolchain.implementation doesn't accept value 'unknownImplementation'. Possible values are [vendor-specific, J9]")
    }
}
