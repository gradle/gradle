/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.scala

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.util.internal.VersionNumber

/**
 * Tests the scala plugin when explicitly declaring the scala version using
 * {@link org.gradle.api.plugins.scala.ScalaPluginExtension#getScalaVersion()}
 */
@TargetCoverage({ ScalaCoverage.SUPPORTED_BY_JDK })
class ScalaPluginExplicitVersionIntegrationTest extends MultiVersionIntegrationSpec {

    def "can compile scala code"() {
        buildFile << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            scala {
                scalaVersion = "${version}"
            }
        """

        file("src/main/scala/Main.scala") << """
            object Main {
                def main(args: Array[String]): Unit = {
                    println("Hello, world!")
                }
            }
        """

        when:
        succeeds("compileScala")

        then:
        file("build/classes/scala/main/Main.class").exists()
    }

    def "can compile 2.13 code"() {
        buildFile << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            scala {
                scalaVersion = "${version}"
            }
        """

        file("src/main/scala/Main.scala") << """
            object Main {
              def main(args: Array[String]): Unit = {
                val grouped = List(1, 2, 3).groupMap(_ % 2)(_ * 2) // Introduced in 2.13.x
                println(grouped)
              }
            }
        """

        expect:
        if (VersionNumber.parse(version) >= VersionNumber.parse("2.13")) {
            succeeds("compileScala")
            file("build/classes/scala/main/Main.class").exists()
        } else {
            fails("compileScala")
            file("build/classes/scala/main/Main.class").assertDoesNotExist()
        }
    }

    def "can compile 3.1 code"() {
        buildFile << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            scala {
                scalaVersion = "${version}"
            }
        """

        file("src/main/scala/Main.scala") << """
            object Main {
              inline def add(x: Int, y: Int): Int = x + y // Uses `inline` keyword

              def main(args: Array[String]): Unit = {
                println(add(1, 2))
              }
            }
        """

        expect:
        if (VersionNumber.parse(version) >= VersionNumber.parse("3.1")) {
            succeeds("compileScala")
            file("build/classes/scala/main/Main.class").exists()
        } else {
            fails("compileScala")
            file("build/classes/scala/main/Main.class").assertDoesNotExist()
        }
    }
}
