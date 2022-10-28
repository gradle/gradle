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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata

class JavaToolchainSpecIntegrationTest extends AbstractIntegrationSpec {

    def "nag user about invalid toolchain spec when #description"() {
        buildScript """
            apply plugin: "java"

            def launcher = javaToolchains.launcherFor {
                $configureInvalid
            }

            task unpackLauncher {
                doFirst {
                    println launcher.getOrNull()
                }
            }
        """

        when:
        run ':help'
        then:
        executedAndNotSkipped ':help'

        when:
        // deprecation warning is lazy
        executer.expectDocumentedDeprecationWarning("Using toolchain specifications without setting a language version has been deprecated. This will fail with an error in Gradle 8.0. Consider configuring the language version. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#invalid_toolchain_specification_deprecation")
        run ':unpackLauncher'
        then:
        executedAndNotSkipped ':unpackLauncher'

        where:
        description                                | configureInvalid
        "only vendor is configured"                | 'vendor = JvmVendorSpec.AZUL'
        "only implementation is configured"        | 'implementation = JvmImplementation.J9'
        "vendor and implementation are configured" | 'vendor = JvmVendorSpec.AZUL; implementation = JvmImplementation.J9'
    }

    def "do not nag user when toolchain spec is valid (#description)"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())

        buildScript """
            apply plugin: "java"

            javaToolchains.launcherFor {
                ${languageVersion ? "languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})" : ""}
                ${vendor ? "vendor = JvmVendorSpec.matching('${jdkMetadata.vendor.rawVendor}')" : ""}
            }.getOrNull()
        """

        when:
        withInstallations(jdkMetadata).run ':help'

        then:
        executedAndNotSkipped ':help'

        where:
        description                                 | languageVersion | vendor
        "configured with language version"          | true            | false
        "configured not only with language version" | true            | true
        "unconfigured"                              | false           | false
    }

    private withInstallations(JvmInstallationMetadata... jdkMetadata) {
        def installationPaths = jdkMetadata.collect { it.javaHome.toAbsolutePath().toString() }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }
}
