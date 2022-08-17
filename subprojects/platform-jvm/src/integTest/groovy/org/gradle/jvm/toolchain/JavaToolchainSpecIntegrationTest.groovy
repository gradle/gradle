/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaToolchainSpecIntegrationTest extends AbstractIntegrationSpec {

    def "nag user about invalid toolchain spec when #description"() {
        buildScript """
            apply plugin: "java"

            ${mavenCentralRepository()}

            javaToolchains.launcherFor {
                $configureInvalid
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Using toolchain specifications without setting a language version has been deprecated. This will fail with an error in Gradle 8.0. Consider configuring the language version. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#invalid_toolchain_specification_deprecation")
        run ':help'

        then:
        executedAndNotSkipped ':help'

        where:
        description                                | configureInvalid
        "only vendor is configured"                | 'vendor = JvmVendorSpec.AZUL'
        "only implementation is configured"        | 'implementation = JvmImplementation.J9'
        "vendor and implementation are configured" | 'vendor = JvmVendorSpec.AZUL; implementation = JvmImplementation.J9'
    }

    def "do not nag user when toolchain spec is valid (#description)"() {
        buildScript """
            apply plugin: "java"

            ${mavenCentralRepository()}

            javaToolchains.launcherFor {
                $configureInvalid
            }
        """

        when:
        run ':help'

        then:
        executedAndNotSkipped ':help'

        where:
        description                                 | configureInvalid
        "configured with language version"          | 'languageVersion = JavaLanguageVersion.of(9)'
        "configured not only with language version" | 'languageVersion = JavaLanguageVersion.of(9); vendor = JvmVendorSpec.AZUL'
        "unconfigured"                              | ''
    }
}
