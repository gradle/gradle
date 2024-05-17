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

package org.gradle.api.plugins.quality.pmd

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

/**
 * Tests to ensure toolchains specified by the {@code PmdPlugin} and
 * {@code Pmd} tasks behave as expected.
 */
class PmdPluginToolchainsIntegrationTest extends AbstractPmdPluginVersionIntegrationTest implements JavaToolchainFixture {

    def setup() {
        executer.withArgument("--info")
    }

    def "uses jdk from toolchains set through java plugin"() {
        Assume.assumeTrue(fileLockingIssuesSolved())
        given:
        goodCode()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromJavaPlugin(jdk)

        when:
        succeeds("pmdMain")

        then:
        outputContains("Running pmd with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses jdk from toolchains set through pmd task"() {
        Assume.assumeTrue(fileLockingIssuesSolved())
        given:
        goodCode()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromCheckstyleTask(jdk)

        when:
        succeeds("pmdMain")

        then:
        outputContains("Running pmd with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses current jdk if not specified otherwise"() {
        Assume.assumeTrue(fileLockingIssuesSolved())
        given:
        goodCode()
        writeBuildFile()

        when:
        succeeds("pmdMain")

        then:
        outputContains("Running pmd with toolchain '${Jvm.current().javaHome.absolutePath}'")
    }

    def "uses current jdk if pmd plugin is not applied"() {
        given:
        goodCode()
        setupExecutorForToolchains()
        writeBuildFileWithoutApplyingPmdPlugin()
        buildFile << """
            Map<String, String> excludeProperties(String group, String module) {
                return ["group": group, "module": module]
            }
            Configuration configuration = configurations.create("pmd")
            configuration.exclude(excludeProperties("ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"))
            configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"))
            configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"))
            configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"))
            configuration.exclude(excludeProperties("commons-logging", "commons-logging"))
            configuration.exclude(excludeProperties("log4j", "log4j"))
            dependencies.add("pmd", dependencies.create("${calculateDefaultDependencyNotation()}"))
            FileCollection pmdFileCollection = configuration

            tasks.register("myPmd", Pmd) {
                maxFailures = 0
                source = fileTree("\$projectDir/src/main")
                pmdClasspath = pmdFileCollection
                ruleSetFiles = files()
                ruleSets = ["category/java/errorprone.xml"]
                rulesMinimumPriority = 5
                targetJdk = TargetJdk.VERSION_1_4
                threads = 1
                incrementalAnalysis = false
            }
        """

        when:
        succeeds("myPmd")

        then:
        outputContains("Running pmd with toolchain '${Jvm.current().javaHome.absolutePath}'.")
    }

    Jvm setupExecutorForToolchains() {
        Jvm jdk = AvailableJavaHomes.getDifferentVersion()
        withInstallations(jdk)
        return jdk
    }

    private void writeBuildFile() {
        buildFile << """
            plugins {
                id 'groovy'
                id 'java'
                id 'pmd'
            }

            dependencies {
                pmd "${calculateDefaultDependencyNotation()}"
            }

            ${mavenCentralRepository()}
        """

        if (versionNumber < VersionNumber.version(6)) {
            buildFile << """
                pmd {
                    incrementalAnalysis = false
                }
            """
        }
    }

    private void writeBuildFileWithoutApplyingPmdPlugin() {
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
        configureJavaPluginToolchainVersion(jvm)
    }

    private void writeBuildFileWithToolchainsFromCheckstyleTask(Jvm jvm) {
        writeBuildFile()
        buildFile << """
            tasks.withType(Pmd) {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
                }
            }
        """
    }

    private goodCode() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean isFoo(Object arg) { return true; } }"
    }
}
