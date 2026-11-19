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

package org.gradle.scala.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

/**
 * Verifies the target parameter Gradle deduces from the Java toolchain against real Scala compilers.
 */
@Issue("https://github.com/gradle/gradle/issues/36260")
class ScalaCompileTargetDeductionIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/scala/Greeter.scala") << """
            trait Named { def name: String = "named" }
            trait Loud extends Named { override def name: String = super.name.toUpperCase }

            class Greeter extends Loud {
                private val suffix = "!"
                def greet(names: Seq[String]): Seq[String] = names.map(n => s"Hello, \$n (\$name)\$suffix")
            }
        """
        file("src/test/scala/GreeterTest.scala") << """
            import _root_.org.junit.Test

            class GreeterTest {
                @Test
                def greets(): Unit = assert(new Greeter().greet(Seq("Scala")) == Seq("Hello, Scala (NAMED)!"))
            }
        """
    }

    private void scalaBuild(String scalaVersion) {
        buildFile << """
            apply plugin: "scala"
            ${mavenCentralRepository()}

            dependencies {
                implementation "${scalaDependency(scalaVersion)}"
                testImplementation "junit:junit:4.13"
            }

            test {
                useJUnit()
            }

            compileScala {
                doLast {
                    logger.lifecycle("scalaCompileOptions.additionalParameters = \${scalaCompileOptions.additionalParameters}")
                }
            }
        """
    }

    def "deduces #expectedParameter for Scala #scalaVersion with Java 17 toolchain"() {
        def jdk17 = AvailableJavaHomes.getJdk17()
        Assume.assumeNotNull(jdk17)

        scalaBuild(scalaVersion)
        configureJavaPluginToolchainVersion(jdk17)

        when:
        withInstallations(jdk17).succeeds(":test")

        then:
        outputContains("scalaCompileOptions.additionalParameters = [$expectedParameter]")
        JavaVersion.forClass(scalaClassFile("Greeter.class").bytes) == expectedBytecode

        where:
        scalaVersion | expectedParameter                 | expectedBytecode
        '2.11.12'    | '-target:jvm-1.8'                 | JavaVersion.VERSION_1_8
        '2.12.15'    | '-target:jvm-1.8, -release, 17'   | JavaVersion.VERSION_1_8
        '2.13.0'     | '-target:jvm-1.8, -release, 17'   | JavaVersion.VERSION_1_8
        '2.12.16'    | '-target:17'                      | JavaVersion.VERSION_17
        '2.12.17'    | '-release:17'                     | JavaVersion.VERSION_1_8
        '2.12.20'    | '-release:17'                     | JavaVersion.VERSION_1_8
        '2.13.16'    | '-release:17'                     | JavaVersion.VERSION_17
    }

    def "deduces #expectedParameter for Scala #scalaVersion without configured toolchain"() {
        // Scala 2.12.16 and 2.12.17 support Java 19 at most
        Assume.assumeTrue(Jvm.current().javaVersion <= JavaVersion.VERSION_19)

        scalaBuild(scalaVersion)

        when:
        succeeds(":test")

        then:
        outputContains("scalaCompileOptions.additionalParameters = [$expectedParameter]")
        JavaVersion.forClass(scalaClassFile("Greeter.class").bytes) == JavaVersion.VERSION_1_8

        where:
        scalaVersion | expectedParameter
        '2.12.16'    | '-target:8'
        '2.12.17'    | '-target:8'
        '2.12.20'    | '-target:8'
        '2.13.16'    | '-target:8'
    }
}
