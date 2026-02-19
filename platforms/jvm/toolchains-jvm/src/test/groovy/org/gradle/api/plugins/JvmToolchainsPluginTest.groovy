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

package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.internal.Actions
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainNotFoundMode
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JavadocTool
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainSpecInternal
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import org.jspecify.annotations.Nullable

class JvmToolchainsPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(JvmToolchainsPlugin)
    }

    def "available under jvm-toolchains id"() {
        expect:
        project.pluginManager.hasPlugin("jvm-toolchains")
    }

    def "registers javaToolchains extension"() {
        expect:
        project.extensions.getByType(JavaToolchainService) == project.extensions.getByName("javaToolchains")
    }

    def "toolchain service dependencies are satisfied"() {
        expect:
        project.extensions.getByType(JavaToolchainService).launcherFor(Actions.doNothing()).get().executablePath.asFile.isFile()
    }

    def "compilerFor - when toolchain is unavailable - expect provider throws"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaCompiler> compiler = javaToolchains.compilerFor(javaToolchainSpec(999, optional))
        compiler.getOrNull()

        then:
        thrown ToolchainProvisioningException

        where:
        optional << [false, null]
    }

    def "compilerFor - when optional toolchain is unavailable - expect provider returns null"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaCompiler> compiler = javaToolchains.compilerFor(toolchainSpec)

        then:
        compiler.getOrNull() == null

        where:
        toolchainSpec << [
            javaToolchainSpecAction(999, true),
            javaToolchainSpec(999, true),
        ]
    }

    def "compilerFor - chained providers"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaCompiler> compiler = javaToolchains.compilerFor(javaToolchainSpec(999, true))
            .orElse(javaToolchains.compilerFor(javaToolchainSpec(998, true)))
            .orElse(javaToolchains.compilerFor(javaToolchainSpec(997, true)))
            .orElse(javaToolchains.compilerFor(Actions.doNothing()))

        then:
        compiler.get().metadata.languageVersion.asInt() == Jvm.current().javaVersionMajor
        compiler.get().executablePath.asFile.isFile()
    }

    def "launcherFor - when toolchain is unavailable - expect provider throws"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaLauncher> launcher = javaToolchains.launcherFor(javaToolchainSpec(999, optional))
        launcher.getOrNull()

        then:
        thrown ToolchainProvisioningException

        where:
        optional << [false, null]
    }

    def "launcherFor - when optional toolchain is unavailable - expect provider returns null"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaLauncher> launcher = javaToolchains.launcherFor(toolchainSpec)

        then:
        launcher.getOrNull() == null

        where:
        toolchainSpec << [
            javaToolchainSpecAction(999, true),
            javaToolchainSpec(999, true),
        ]
    }

    def "launcherFor - chained providers"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavaLauncher> launcher = javaToolchains.launcherFor(javaToolchainSpec(999, true))
            .orElse(javaToolchains.launcherFor(javaToolchainSpec(998, true)))
            .orElse(javaToolchains.launcherFor(javaToolchainSpec(997, true)))
            .orElse(javaToolchains.launcherFor(Actions.doNothing()))

        then:
        launcher.get().metadata.languageVersion.asInt() == Jvm.current().javaVersionMajor
        launcher.get().executablePath.asFile.isFile()
    }

    def "javadocTool - when toolchain is unavailable - expect provider throws"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavadocTool> javadoc = javaToolchains.javadocToolFor(javaToolchainSpec(999, optional))
        javadoc.getOrNull()

        then:
        thrown ToolchainProvisioningException

        where:
        optional << [false, null]
    }

    def "javadocTool - when optional toolchain is unavailable - expect provider returns null"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavadocTool> javadoc = javaToolchains.javadocToolFor(toolchainSpec)

        then:
        javadoc.getOrNull() == null

        where:
        toolchainSpec << [
            javaToolchainSpecAction(999, true),
            javaToolchainSpec(999, true),
        ]
    }

    def "javadocTool - chained providers"() {
        given:
        def javaToolchains = project.extensions.getByType(JavaToolchainService)

        when:
        Provider<JavadocTool> javadoc = javaToolchains.javadocToolFor(javaToolchainSpec(999, true))
            .orElse(javaToolchains.javadocToolFor(javaToolchainSpec(998, true)))
            .orElse(javaToolchains.javadocToolFor(javaToolchainSpec(997, true)))
            .orElse(javaToolchains.javadocToolFor(Actions.doNothing()))

        then:
        javadoc.get().metadata.languageVersion.asInt() == Jvm.current().javaVersionMajor
        javadoc.get().executablePath.asFile.isFile()
    }

    private static Action<JavaToolchainSpec> javaToolchainSpecAction(
        int languageVersion,
        Boolean optional
    ) {
        return new Action<JavaToolchainSpec>() {
            @Override
            void execute(JavaToolchainSpec spec) {
                spec.languageVersion.set(JavaLanguageVersion.of(languageVersion))
                if (optional == true) {
                    spec.onNoMatchFound.set(JavaToolchainNotFoundMode.IGNORE)
                } else if (optional == false) {
                    spec.onNoMatchFound.set(JavaToolchainNotFoundMode.THROW_EXCEPTION)
                }
            }
        }
    }

    private JavaToolchainSpec javaToolchainSpec(int languageVersion, @Nullable Boolean optional) {
        return Mock(JavaToolchainSpecInternal) { spec ->
            spec.getLanguageVersion() >> TestUtil.propertyFactory().property(JavaLanguageVersion).value(JavaLanguageVersion.of(languageVersion))
            spec.isValid() >> true
            spec.isConfigured() >> true
            spec.getNativeImageCapable() >>
                TestUtil.propertyFactory().property(Boolean)
            spec.getVendor() >>
                TestUtil.propertyFactory().property(JvmVendorSpec).value(JvmVendorSpec.ADOPTOPENJDK)
            spec.getImplementation() >>
                TestUtil.propertyFactory().property(JvmImplementation).value(JvmImplementation.VENDOR_SPECIFIC)

            def onMissingToolchain = TestUtil.propertyFactory().property(JavaToolchainNotFoundMode)
            if (optional == true) {
                spec.getOnNoMatchFound() >>
                    onMissingToolchain.value(JavaToolchainNotFoundMode.IGNORE)
            } else if (optional == false) {
                spec.getOnNoMatchFound() >>
                    onMissingToolchain.value(JavaToolchainNotFoundMode.THROW_EXCEPTION)
            } else {
                spec.getOnNoMatchFound() >>
                    onMissingToolchain
            }
        }
    }
}
