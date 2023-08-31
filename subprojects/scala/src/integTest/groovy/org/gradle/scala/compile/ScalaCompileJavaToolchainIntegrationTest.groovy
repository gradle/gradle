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

package org.gradle.scala.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

@TargetCoverage({ ScalaCoverage.SUPPORTED_BY_JDK })
class ScalaCompileJavaToolchainIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def getTargetJava8() {
        return versionNumber >= VersionNumber.parse("2.13.9") ? "-release:8"
            : versionNumber >= VersionNumber.parse("2.13.1") ? "-target:8"
            : "-target:jvm-1.8"
    }

    def setup() {
        file("src/main/scala/JavaThing.java") << "public class JavaThing {}"
        file("src/main/scala/ScalaHall.scala") << "class ScalaHall(name: String)"

        buildFile << """
            apply plugin: "scala"
            ${mavenCentralRepository()}

            dependencies {
                implementation "${scalaDependency(version.toString())}"
            }
        """
    }

    def "forkOptions #option is ignored for Scala "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()

        if (option == "executable") {
            buildFile << """
                compileScala {
                    options.fork = true
                    options.forkOptions.executable = "${TextUtil.normaliseFileSeparators(otherJdk.javaExecutable.absolutePath)}"
                }
            """
        } else {
            buildFile << """
                compileScala {
                    options.fork = true
                    options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(otherJdk.javaHome.absolutePath)}")
                }
            """
        }

        when:
        withInstallations(otherJdk).run(":compileScala")

        then:
        executedAndNotSkipped(":compileScala")

        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == currentJdk.javaVersion
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8

        where:
        option << ["executable", "javaHome"]
    }

    def "uses #what toolchain #when for Scala "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        buildFile << """
            compileScala {
                scalaCompileOptions.additionalParameters = ["${targetJava8}"]
            }
        """

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileScala", "--info")

        then:
        executedAndNotSkipped(":compileScala")
        outputContains("Compiling with Zinc Scala compiler")

        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == targetJdk.javaVersion
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8

        where:
        what             | when                         | withTool | withJavaExtension | target
        "current JVM"    | "when nothing is configured" | null     | null              | "current"
        "java extension" | "when configured"            | null     | "other"           | "other"
        "assigned tool"  | "when configured"            | "other"  | null              | "other"
        "assigned tool"  | "over java extension"        | "other"  | "current"         | "other"
    }

    def "up-to-date depends on the toolchain for Scala "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.getDifferentVersion()

        buildFile << """
            compileScala {
                scalaCompileOptions.additionalParameters = ["${targetJava8}"]

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
        withInstallations(currentJdk, otherJdk).run(":compileScala")
        then:
        executedAndNotSkipped(":compileScala")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileScala")
        then:
        skipped(":compileScala")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileScala", "-Pchanged", "--info")
        then:
        executedAndNotSkipped(":compileScala")
        outputContains("Value of input property 'javaLauncher.metadata.languageVersion' has changed for task ':compileScala'")

        when:
        withInstallations(currentJdk, otherJdk).run(":compileScala", "-Pchanged")
        then:
        skipped(":compileScala")
    }

    def "source and target compatibility override toolchain (source #source, target #target) for Scala "() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }

            compileScala {
                scalaCompileOptions.additionalParameters = ["${targetJava8}"]

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
        withInstallations(jdk11).run(":compileScala")

        then:
        executedAndNotSkipped(":compileScala")

        outputContains("project.sourceCompatibility = 11")
        outputContains("project.targetCompatibility = 11")
        outputContains("task.sourceCompatibility = $sourceOut")
        outputContains("task.targetCompatibility = $targetOut")
        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == JavaVersion.toVersion(targetOut)
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8

        where:
        source | target | sourceOut | targetOut
        '9'    | '10'   | '9'       | '10'
        '9'    | 'none' | '9'       | '9'
        'none' | 'none' | '11'      | '11'
    }

    def "can compile source and run tests using Java #javaVersion for Scala "() {
        def jdk = AvailableJavaHomes.getJdk(javaVersion)
        Assume.assumeTrue(jdk != null)

        configureJavaPluginToolchainVersion(jdk)

        buildFile << """
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.additionalParameters = ["${targetJava8}"]
            }

            dependencies {
               testImplementation "junit:junit:4.13"
            }

            test {
                useJUnit()
            }
        """

        file("src/test/scala/ScalaTest.scala") << """
            import _root_.org.junit.Test;

            class ScalaTest {
                @Test
                def verify(): Unit = println("Running Scala test with Java version " + System.getProperty("java.version"))
            }
        """

        when:
        withInstallations(jdk).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Running Scala test with Java version ${jdk.javaVersion}")

        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == javaVersion
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8
        JavaVersion.forClass(classFile("scala", "test", "ScalaTest.class").bytes) == JavaVersion.VERSION_1_8

        where:
        javaVersion << JavaVersion.values().findAll { JavaVersion.VERSION_1_8 <= it && it <= JavaVersion.VERSION_20 }
    }

    private TestFile configureTool(Jvm jdk) {
        buildFile << """
            compileScala {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }

}
