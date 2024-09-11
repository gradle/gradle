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
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm

class JavaToolchainIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "fails when using an invalid toolchain spec when #description"() {

        buildFile """
            apply plugin: JvmToolchainsPlugin

            abstract class UnpackLauncher extends DefaultTask {
                @Nested
                abstract Property<JavaLauncher> getLauncher()

                @TaskAction
                void useLauncher() {
                    // we never get here
                    launcher.getOrNull()
                }
            }

            task unpackLauncher(type: UnpackLauncher) {
                launcher.set(javaToolchains.launcherFor {
                    $configureInvalid
                })
            }
        """

        when:
        fails ':unpackLauncher'
        then:
        failure.assertHasDocumentedCause("Using toolchain specifications without setting a language version is not supported. Consider configuring the language version. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#invalid_toolchain_specification_deprecation")

        where:
        description                                | configureInvalid
        "only vendor is configured"                | 'vendor = JvmVendorSpec.AZUL'
        "only implementation is configured"        | 'implementation = JvmImplementation.J9'
        "vendor and implementation are configured" | 'vendor = JvmVendorSpec.AZUL; implementation = JvmImplementation.J9'
    }

    def "do not nag user when toolchain spec is valid (#description)"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())

        buildFile """
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

    def "identify whether #tool toolchain corresponds to the #current JVM"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jvm as Jvm)

        buildFile """
            apply plugin: "java"

            def tool = javaToolchains.${toolMethod} {
                languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
            }.get()

            println("Toolchain isCurrentJvm=" + tool.metadata.isCurrentJvm())
        """

        when:
        withInstallations(jdkMetadata).run ':help'

        then:
        outputContains("Toolchain isCurrentJvm=${isCurrentJvm}")

        where:
        tool          | isCurrentJvm | jvm
        "compiler"    | true         | Jvm.current()
        "compiler"    | false        | AvailableJavaHomes.differentVersion
        "launcher"    | true         | Jvm.current()
        "launcher"    | false        | AvailableJavaHomes.differentVersion
        "javadocTool" | true         | Jvm.current()
        "javadocTool" | false        | AvailableJavaHomes.differentVersion

        and:
        toolMethod = "${tool}For"
        current = (isCurrentJvm ? "current" : "non-current")
    }

    def "fails when trying to change java extension toolchain spec property after it has been used to resolve a toolchain"() {
        def jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        def jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        buildFile """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata1.languageVersion.majorVersion})
                }
            }

            project.getExtensions().getByType(JavaToolchainService.class)
                .launcherFor(java.toolchain)
                .get()

            java.toolchain.languageVersion.set(JavaLanguageVersion.of(${jdkMetadata2.languageVersion.majorVersion}))
        """

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).runAndFail ':customTask'

        then:
        failure.assertHasCause("The value for property 'languageVersion' is final and cannot be changed any further")
    }

    def "fails when trying to change captured toolchain spec property after it has been used to resolve a toolchain"() {
        def jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        def jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)
        buildFile """
            import java.util.concurrent.atomic.AtomicReference
            import org.gradle.jvm.toolchain.JavaToolchainSpec

            apply plugin: "java"

            def toolchainSpecRef = new AtomicReference<JavaToolchainSpec>()

            javaToolchains.launcherFor {
                toolchainSpecRef.set(delegate)
                languageVersion = JavaLanguageVersion.of(${jdkMetadata1.languageVersion.majorVersion})
            }.get()

            toolchainSpecRef.get().languageVersion.set(JavaLanguageVersion.of(${jdkMetadata2.languageVersion.majorVersion}))
        """

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).runAndFail ':help'

        then:
        failure.assertHasCause("The value for property 'languageVersion' is final and cannot be changed any further")
    }

    def "nag user when toolchain spec is IBM_SEMERU"() {
        given:
        buildFile """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                    vendor = JvmVendorSpec.IBM_SEMERU
                    implementation = JvmImplementation.J9
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning "Requesting JVM vendor IBM_SEMERU. " +
            "This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#ibm_semeru_should_not_be_used"

        then:
        fails ':build'
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
            .assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
    }

    def "does not nag user when toolchain spec is IBM"() {
        given:
        buildFile """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                    vendor = JvmVendorSpec.IBM
                    implementation = JvmImplementation.J9
                }
            }
        """

        expect:
        fails ':build'
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
            .assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
    }
}
