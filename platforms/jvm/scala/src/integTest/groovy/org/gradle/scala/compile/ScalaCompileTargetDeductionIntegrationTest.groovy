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

    def "deduces #expectedParameter for Scala #scalaVersion with Java #java toolchain"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.toVersion(java))
        Assume.assumeNotNull(jdk)

        scalaBuild(scalaVersion)
        configureJavaPluginToolchainVersion(jdk)

        when:
        withInstallations(jdk).succeeds(":test")

        then:
        outputContains("scalaCompileOptions.additionalParameters = [$expectedParameter]")
        JavaVersion.forClass(scalaClassFile("Greeter.class").bytes) == expectedBytecode

        where:
        // Scala 2.12.17+ always emits Java 8 bytecode, -release only selects the Java API (scala/scala#10109)
        java | scalaVersion | expectedParameter                 | expectedBytecode
        8    | '2.11.12'    | '-target:jvm-1.8'                 | JavaVersion.VERSION_1_8
        8    | '2.12.15'    | '-target:jvm-1.8'                 | JavaVersion.VERSION_1_8
        8    | '2.12.16'    | '-target:8'                       | JavaVersion.VERSION_1_8
        8    | '2.12.17'    | '-release:8'                      | JavaVersion.VERSION_1_8
        8    | '2.12.20'    | '-release:8'                      | JavaVersion.VERSION_1_8
        8    | '2.13.16'    | '-release:8'                      | JavaVersion.VERSION_1_8
        11   | '2.12.15'    | '-target:jvm-1.8, -release, 11'   | JavaVersion.VERSION_1_8
        11   | '2.12.20'    | '-release:11'                     | JavaVersion.VERSION_1_8
        17   | '2.11.12'    | '-target:jvm-1.8'                 | JavaVersion.VERSION_1_8
        17   | '2.12.15'    | '-target:jvm-1.8, -release, 17'   | JavaVersion.VERSION_1_8
        17   | '2.13.0'     | '-target:jvm-1.8, -release, 17'   | JavaVersion.VERSION_1_8
        17   | '2.12.16'    | '-target:17'                      | JavaVersion.VERSION_17
        17   | '2.12.17'    | '-release:17'                     | JavaVersion.VERSION_1_8
        17   | '2.12.20'    | '-release:17'                     | JavaVersion.VERSION_1_8
        17   | '2.13.16'    | '-release:17'                     | JavaVersion.VERSION_17
        17   | '3.7.4'      | '-release:17'                     | JavaVersion.VERSION_17
        17   | '3.8.4'      | '-release:17'                     | JavaVersion.VERSION_17
    }

    def "deduces #expectedParameter for Scala #scalaVersion without configured toolchain"() {
        Assume.assumeTrue(Jvm.current().javaVersion <= maxJava)

        scalaBuild(scalaVersion)

        when:
        succeeds(":test")

        then:
        outputContains("scalaCompileOptions.additionalParameters = [$expectedParameter]")
        JavaVersion.forClass(scalaClassFile("Greeter.class").bytes) == expectedBytecode

        where:
        scalaVersion | expectedParameter | expectedBytecode        | maxJava
        '2.12.16'    | '-target:8'       | JavaVersion.VERSION_1_8 | JavaVersion.VERSION_19
        '2.12.17'    | '-target:8'       | JavaVersion.VERSION_1_8 | JavaVersion.VERSION_19
        '2.12.20'    | '-target:8'       | JavaVersion.VERSION_1_8 | JavaVersion.VERSION_23
        '2.13.16'    | '-target:8'       | JavaVersion.VERSION_1_8 | JavaVersion.VERSION_23
        '3.7.4'      | '-Xtarget:8'      | JavaVersion.VERSION_1_8 | JavaVersion.VERSION_25
        '3.8.4'      | '-Xtarget:17'     | JavaVersion.VERSION_17  | JavaVersion.VERSION_25
    }

    def "fails for Scala 3.8 with Java #java toolchain"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.toVersion(java))
        Assume.assumeNotNull(jdk)

        scalaBuild("3.8.4")
        configureJavaPluginToolchainVersion(jdk)

        when:
        withInstallations(jdk).fails(":compileScala")

        then:
        failure.assertHasCause("Scala 3.8.4 requires Java 17 or later, but the Java toolchain is Java $java. Use a Java toolchain of version 17 or later, or a Scala version before 3.8.")

        where:
        java << [8, 11]
    }

    def "scaladoc fails for Scala 3.8 with Java #java launcher"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.toVersion(java))
        Assume.assumeNotNull(jdk)

        scalaBuild("3.8.4")
        buildFile << """
            tasks.withType(ScalaDoc).configureEach {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of($java)
                }
            }
        """

        when:
        withInstallations(jdk).fails(":scaladoc")

        then:
        executedAndNotSkipped(":compileScala")
        failure.assertHasCause("Scala 3.8.4 requires Java 17 or later, but the Java toolchain is Java $java. Use a Java toolchain of version 17 or later, or a Scala version before 3.8.")

        where:
        java << [8, 11]
    }
}
