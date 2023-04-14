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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

@TargetCoverage({ ScalaCoverage.DEFAULT })
class ScalaCompileIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        buildFile << """
            apply plugin: "scala"
            ${mavenCentralRepository()}

            dependencies {
                implementation "${scalaDependency(version)}"
            }
        """
    }

    def "can compile sources"() {
        def currentJdk = Jvm.current()

        file("src/main/scala/JavaThing.java") << "public class JavaThing {}"
        file("src/main/scala/ScalaHall.scala") << "class ScalaHall(name: String)"

        when:
        run ":compileScala"

        then:
        executedAndNotSkipped(":compileScala")

        JavaVersion.forClass(scalaClassFile("JavaThing.class").bytes) == currentJdk.javaVersion
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8
    }

    def "fail in case of errors and print correct positions"() {
        given:
        file("src/main/scala/Person.scala") << """
class Person {
    def getName(): String = 42
}
"""
        expect:
        fails(":compileScala")
        if (versionNumber.major >= 3) {
            result.assertHasErrorOutput("src/main/scala/Person.scala:3:29: Found:    (42 : Int)\nRequired: String")
        } else {
            result.assertHasErrorOutput("src/main/scala/Person.scala:3:29: type mismatch;\n found   : Int(42)\n required: String")
        }
    }

    def "can assign #value to additional parameters"() {
        file("src/main/scala/ScalaHall.scala") << "class ScalaHall(name: String)"

        buildFile << """
            tasks.withType(ScalaCompile) {
              scalaCompileOptions.additionalParameters = $expression
            }
        """

        when:
        run ":compileScala"

        then:
        executedAndNotSkipped(":compileScala")
        JavaVersion.forClass(scalaClassFile("ScalaHall.class").bytes) == JavaVersion.VERSION_1_8

        where:
        value            | expression
        "null"           | "null"
        "mutable list"   | "[]"
        "immutable list" | "[].asImmutable()"
    }

    @Requires(TestPrecondition.JDK11_OR_LATER)
    def "can compile sources using later JDK APIs"() {
        file("src/main/scala/App.scala") << """
            object App {
              def main(args: Array[String]): Unit = {
                var x : String = "Test String"
                // isBlank is from JDK 11
                println(x.isBlank)
              }
            }
        """

        when:
        run ":compileScala"

        then:
        executedAndNotSkipped(":compileScala")

        JavaVersion.forClass(scalaClassFile("App.class").bytes) == JavaVersion.VERSION_1_8
    }

}
