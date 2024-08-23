/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainBuildOperationsFixture
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

class UpToDateScalaCompileIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture, JavaToolchainBuildOperationsFixture {

    def setup() {
        file('src/main/scala/Person.scala') << "class Person(name: String)"
    }

    @Requires(value = UnitTestPreconditions.Jdk23OrEarlier, reason = "Scala does not work with Java 24 without warnings yet")
    def "compile is out of date when changing the #changedVersion version"() {
        buildFile(scalaProjectBuildScript(defaultZincVersion, defaultScalaVersion))

        when:
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        run 'compileScala'

        then:
        skipped ':compileScala'

        when:
        buildFile(scalaProjectBuildScript(newZincVersion, newScalaVersion))
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        where:
        newScalaVersion  | newZincVersion
        '2.11.12'        | '1.6.0'
        '2.12.18'        | '1.6.1'
        defaultScalaVersion = '2.11.12'
        defaultZincVersion = ScalaBasePlugin.DEFAULT_ZINC_VERSION
        changedVersion = defaultScalaVersion != newScalaVersion ? 'scala' : 'zinc'
    }

    @Requires(value = [
        IntegTestPreconditions.Java17HomeAvailable,
        IntegTestPreconditions.Java21HomeAvailable,
        IntegTestPreconditions.NotEmbeddedExecutor,
    ], reason = "must run with specific JDK versions")
    def "compile is out of date when changing the java version"() {
        def jdk17 = AvailableJavaHomes.jdk17
        def jdk21 = AvailableJavaHomes.jdk21

        buildFile(scalaProjectBuildScript(ScalaBasePlugin.DEFAULT_ZINC_VERSION, ScalaCoverage.getLatestSupportedScala2Version()))
        when:
        executer.withJvm(jdk17)
        run 'compileScala'

        then:
        executedAndNotSkipped(':compileScala')

        when:
        executer.withJvm(jdk17)
        run 'compileScala'
        then:
        skipped ':compileScala'

        when:
        executer.withJvm(jdk21)
        run 'compileScala', '--info'
        then:
        executedAndNotSkipped(':compileScala')
    }

    def scalaProjectBuildScript(String zincVersion, String scalaVersion) {
        return """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.scala-lang:scala-library:${scalaVersion}"
            }

            scala {
                zincVersion = "${zincVersion}"
            }

            java {
                sourceCompatibility = '1.7'
                targetCompatibility = '1.7'
            }
        """.stripIndent()
    }

    @Requires(value = [
        IntegTestPreconditions.Java21HomeAvailable,
        IntegTestPreconditions.Java24HomeAvailable,
        IntegTestPreconditions.NotEmbeddedExecutor,
    ], reason = "must run with specific JDK versions")
    def "compile is out of date when changing the java launcher"() {
        def jdk21 = AvailableJavaHomes.jdk21
        def jdk24 = AvailableJavaHomes.jdk24

        buildFile """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.scala-lang:scala-library:${ScalaCoverage.latestSupportedScala2Version}"
            }

            scala {
                zincVersion = "1.7.1"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(
                        !providers.gradleProperty("changed").isPresent()
                            ? ${jdk21.javaVersionMajor}
                            : ${jdk24.javaVersionMajor}
                    )
                }
            }

            tasks.withType(ScalaCompile) {
                scalaCompileOptions.with {
                    additionalParameters.add("-target:8")
                }
            }
        """

        when:
        withInstallations(jdk21, jdk24).run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        withInstallations(jdk21, jdk24).run 'compileScala'
        then:
        skipped ':compileScala'

        when:
        withInstallations(jdk21, jdk24).run 'compileScala', '-Pchanged', '--info'
        then:
        executedAndNotSkipped ':compileScala'
        outputContains("Value of input property 'javaLauncher.metadata.taskInputs.languageVersion' has changed for task ':compileScala'")

        when:
        withInstallations(jdk21, jdk24).run 'compileScala', '-Pchanged', '--info'
        then:
        skipped ':compileScala'
    }

    @Requires(value = [
        IntegTestPreconditions.Java21HomeAvailable,
        IntegTestPreconditions.Java24HomeAvailable,
    ], reason = "needed for toolchain use")
    def "compilation emits toolchain usage events"() {
        captureBuildOperations()
        def jdk = AvailableJavaHomes.getDifferentJdk { it.languageVersion.majorVersion.toInteger() in 21..24 }

        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jdk)

        buildFile """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.scala-lang:scala-library:${ScalaCoverage.latestSupportedScala2Version}"
            }

            scala {
                zincVersion = "1.7.1"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            tasks.withType(ScalaCompile) {
                scalaCompileOptions.with {
                    additionalParameters.add("-target:8")
                }
            }
        """

        when:
        withInstallations(jdkMetadata).run ":compileScala"
        def events = toolchainEvents(":compileScala")

        then:
        executedAndNotSkipped ":compileScala"
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        withInstallations(jdkMetadata).run ":compileScala"
        events = toolchainEvents(":compileScala")

        then:
        skipped ":compileScala"
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }
}
