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

package org.gradle.api.plugins.quality.codenarc

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.jvm.Jvm
import org.gradle.quality.integtest.fixtures.CodeNarcCoverage
import org.gradle.test.fixtures.file.TestFile

import static org.junit.Assume.assumeNotNull

/**
 * Tests to ensure toolchains specified by the {@code CodeNarcPlugin} and
 * {@code CodeNarc} tasks behave as expected.
 */
@TargetCoverage({ CodeNarcCoverage.getSupportedVersionsByJdk() })
class CodeNarcPluginToolchainsIntegrationTest extends MultiVersionIntegrationSpec{
    def setup() {
        executer.withArgument("--info")
    }

    def "uses jdk from toolchains set through java plugin"() {
        given:
        goodCode()
        writeRuleFile()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromJavaPlugin(jdk)

        when:
        succeeds("codenarcMain")

        then:
        outputContains("Running codenarc with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses jdk from toolchains set through codenarc task"() {
        given:
        goodCode()
        writeRuleFile()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromCodeNarcTask(jdk)

        when:
        succeeds("codenarcMain")

        then:
        outputContains("Running codenarc with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses current jdk if not specified otherwise"() {
        given:
        goodCode()
        writeRuleFile()
        writeBuildFile()

        when:
        succeeds("codenarcMain")

        then:
        outputContains("Running codenarc with toolchain '${Jvm.current().javaHome.absolutePath}'")
    }

    def "uses current jdk if codenarc plugin is not applied"() {
        given:
        goodCode()
        writeRuleFile()
        setupExecutorForToolchains()
        writeBuildFileWithoutApplyingCodeNarcPlugin()
        buildFile << """
            Map<String, String> excludeProperties(String group, String module) {
                return ["group": group, "module": module]
            }
            Configuration configuration = configurations.create("codenarc")
            configuration.exclude(excludeProperties("ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"))
            configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"))
            configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"))
            configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"))
            configuration.exclude(excludeProperties("commons-logging", "commons-logging"))
            configuration.exclude(excludeProperties("log4j", "log4j"))
            dependencies.add("codenarc", dependencies.create("org.codenarc:CodeNarc:$version"))
            FileCollection codenarcFileCollection = configuration
            tasks.register("myCodenarc", CodeNarc) {
                source = fileTree("\$projectDir/src/main")
                config = project.resources.text.fromFile(file("\$projectDir/config/codenarc/codenarc.xml"))
                codenarcClasspath = codenarcFileCollection
            }
        """

        when:
        succeeds("myCodenarc")

        then:
        outputContains("Running codenarc with toolchain '${Jvm.current().javaHome.absolutePath}'.")
    }

    Jvm setupExecutorForToolchains() {
        Jvm jdk = AvailableJavaHomes.getDifferentVersion()
        assumeNotNull(jdk)
        executer.withArgument("-Porg.gradle.java.installations.paths=${jdk.javaHome.absolutePath}")
        return jdk
    }

    private void writeBuildFile() {
        buildFile << """
    plugins {
        id 'groovy'
        id 'java'
        id 'codenarc'
    }
    codenarc {
        toolVersion = '$version'
    }
    ${mavenCentralRepository()}
"""
    }

    private void writeBuildFileWithoutApplyingCodeNarcPlugin() {
        buildFile << """
    plugins {
        id 'groovy'
        id 'java'
    }
    ${mavenCentralRepository()}
"""
    }

    private void writeBuildFileWithToolchainsFromJavaPlugin(Jvm jvm) {
        writeBuildFile()
        buildFile << """
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
        }
    }
"""
    }

    private void writeBuildFileWithToolchainsFromCodeNarcTask(Jvm jvm) {
        writeBuildFile()
        buildFile << """
    tasks.withType(CodeNarc) {
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
        }
    }
"""
    }

    private goodCode() {
        file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
        file("src/test/groovy/org/gradle/TestClass1.groovy") << "package org.gradle; class TestClass1 { }"
    }

    private TestFile writeRuleFile() {
        file("config/codenarc/codenarc.xml") << """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
                    xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
                <ruleset-ref path="rulesets/naming.xml"/>
            </ruleset>
        """
    }
}
