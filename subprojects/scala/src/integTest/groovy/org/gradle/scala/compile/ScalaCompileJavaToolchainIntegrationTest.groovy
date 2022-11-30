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

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

@TargetCoverage({ ScalaCoverage.DEFAULT })
class ScalaCompileJavaToolchainIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/scala/JavaThing.java") << "public class JavaThing {}"
        file("src/main/scala/ScalaHall.scala") << "class ScalaHall(name: String)"

        buildFile << """
            apply plugin: "scala"
            ${mavenCentralRepository()}

            dependencies {
                implementation "${scalaDependency(version)}"
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
        outputDoesNotContain("[Warn]")

        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == currentJdk.javaVersion
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8

        where:
        option << ["executable", "javaHome"]
    }

    def "uses #what toolchain #when for Scala "() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        def targetParam =
            versionNumber >= VersionNumber.parse("2.13.9") ? "-release:8"
                : versionNumber >= VersionNumber.parse("2.13.1") ? "-target:8"
                : "-target:jvm-1.8"

        buildFile << """
            compileScala {
                scalaCompileOptions.additionalParameters = ["${targetParam}"]
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
