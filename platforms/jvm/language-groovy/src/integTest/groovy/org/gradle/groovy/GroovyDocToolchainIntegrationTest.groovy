/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.testing.fixture.GroovyCoverage
import org.junit.Assume

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({GroovyCoverage.SUPPORTS_GROOVYDOC})
class GroovyDocToolchainIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/groovy/pkg/Thing.groovy") << """
            package pkg

            class Thing {}
        """

        buildFile << """
            plugins {
                id("groovy")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation "${groovyModuleDependency("groovy", versionNumber)}"
            }
        """
    }

    def "uses #what toolchain #when for Groovy "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()
        Assume.assumeTrue("Requires a JDK different from the current one", otherJdk != null)
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        if (withTool != null) {
            configureGroovydocTool(selectJdk(withTool))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":groovydoc", "--info")

        then:
        executedAndNotSkipped(":groovydoc")
        outputContains("Running groovydoc with toolchain '${targetJdk.javaHome.absolutePath}'")
        file("build/docs/groovydoc/pkg/Thing.html").exists()

        where:
        what             | when                         | withTool | withJavaExtension | target
        "current JVM"    | "when nothing is configured" | null     | null              | "current"
        "java extension" | "when configured"            | null     | "other"           | "other"
        "assigned tool"  | "when configured"            | "other"  | null              | "other"
        "assigned tool"  | "over java extension"        | "other"  | "current"         | "other"
    }

    def "up-to-date depends on the toolchain for Groovy "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()
        Assume.assumeTrue("Requires a JDK different from the current one", otherJdk != null)

        buildFile << """
            groovydoc {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(
                        providers.gradleProperty("changed").isPresent()
                            ? ${otherJdk.javaVersion.majorVersion}
                            : ${currentJdk.javaVersion.majorVersion}
                    )
                }
            }
        """

        when:
        withInstallations(currentJdk, otherJdk).run(":groovydoc")
        then:
        executedAndNotSkipped(":groovydoc")

        when:
        withInstallations(currentJdk, otherJdk).run(":groovydoc")
        then:
        skipped(":groovydoc")

        when:
        withInstallations(currentJdk, otherJdk).run(":groovydoc", "-Pchanged", "--info")
        then:
        executedAndNotSkipped(":groovydoc")
        outputContains("Value of input property 'javaLauncher.metadata.languageVersion' has changed for task ':groovydoc'")

        when:
        withInstallations(currentJdk, otherJdk).run(":groovydoc", "-Pchanged")
        then:
        skipped(":groovydoc")
    }

    private void configureGroovydocTool(Jvm jdk) {
        buildFile << """
            groovydoc {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }
}
