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

package org.gradle.groovy.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.TextUtil

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({ GroovyCoverage.SINCE_3_0 })
class GroovyCompileToolchainIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/groovy/Foo.java") << "public class Foo {}"
        file("src/main/groovy/Bar.groovy") << "public class Bar { def bar() {} }"

        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}

            dependencies {
                implementation "${groovyModuleDependency("groovy", version)}"
            }
        """
    }

    def "forkOptions #option is ignored"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()

        if (option == "executable") {
            buildFile << """
                compileGroovy {
                    options.fork = true
                    options.forkOptions.executable = "${TextUtil.normaliseFileSeparators(otherJdk.javaExecutable.absolutePath)}"
                }
            """
        } else {
            buildFile << """
                compileGroovy {
                    options.fork = true
                    options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(otherJdk.javaHome.absolutePath)}")
                }
            """
        }

        def groovyTarget = GroovyCoverage.getEffectiveTarget(versionNumber, currentJdk.javaVersion)

        when:
        withInstallations(otherJdk).run(":compileGroovy")

        then:
        executedAndNotSkipped(":compileGroovy")
        currentJdk.javaVersion == JavaVersion.forClass(groovyClassFile("Foo.class").bytes)
        groovyTarget == JavaVersion.forClass(groovyClassFile("Bar.class").bytes)

        where:
        option << ["executable", "javaHome"]
    }

    def "uses #what toolchain #when"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)
        def groovyTarget = GroovyCoverage.getEffectiveTarget(versionNumber, targetJdk.javaVersion)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileGroovy", "--info")

        then:
        executedAndNotSkipped(":compileGroovy")
        outputContains("Compiling with JDK Java compiler API")
        targetJdk.javaVersion == JavaVersion.forClass(groovyClassFile("Foo.class").bytes)
        groovyTarget == JavaVersion.forClass(groovyClassFile("Bar.class").bytes)

        where:
        what             | when                         | withTool | withJavaHome | withExecutable | withJavaExtension | target
        "current JVM"    | "when nothing is configured" | null     | null         | null           | null              | "current"
        "java extension" | "when configured"            | null     | null         | null           | "other"           | "other"
        "assigned tool"  | "when configured"            | "other"  | null         | null           | null              | "other"
        "assigned tool"  | "over java extension"        | "other"  | null         | null           | "current"         | "other"
    }

    def "up-to-date depends on the toolchain"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()

        buildFile << """
            compileGroovy {
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
        withInstallations(currentJdk, otherJdk).run(":compileGroovy")
        then:
        executedAndNotSkipped(":compileGroovy")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileGroovy")
        then:
        skipped(":compileGroovy")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileGroovy", "-Pchanged", "--info")
        then:
        executedAndNotSkipped(":compileGroovy")
        outputContains("Value of input property 'groovyCompilerJvmVersion' has changed for task ':compileGroovy'")
        outputContains("Value of input property 'javaLauncher.metadata.languageVersion' has changed for task ':compileGroovy'")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileGroovy", "-Pchanged")
        then:
        skipped(":compileGroovy")
    }

    def 'source and target compatibility override toolchain (source #source, target #target)'() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }

            compileGroovy {
                ${source != 'none' ? "sourceCompatibility = JavaVersion.toVersion($source)" : ''}
                ${target != 'none' ? "targetCompatibility = JavaVersion.toVersion($target)" : ''}
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
        """

        when:
        withInstallations(jdk11).run(":compileGroovy")

        then:
        outputContains("project.sourceCompatibility = 11")
        outputContains("project.targetCompatibility = 11")
        outputContains("task.sourceCompatibility = $sourceOut")
        outputContains("task.targetCompatibility = $targetOut")
        JavaVersion.toVersion(targetOut) == JavaVersion.forClass(groovyClassFile("Foo.class").bytes)
        JavaVersion.toVersion(targetOut) == JavaVersion.forClass(groovyClassFile("Bar.class").bytes)

        where:
        source | target | sourceOut | targetOut
        '9'    | '10'   | '9'       | '10'
        '9'    | 'none' | '9'       | '9'
        'none' | 'none' | '11'      | '11'
    }

    private TestFile configureTool(Jvm jdk) {
        buildFile << """
            compileGroovy {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }

}
