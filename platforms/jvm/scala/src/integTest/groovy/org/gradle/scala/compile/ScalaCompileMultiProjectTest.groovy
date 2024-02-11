/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture

class ScalaCompileMultiProjectTest extends AbstractIntegrationSpec implements JavaToolchainFixture {
    def setup() {
        buildFile << """
            subprojects {
                repositories {
                    mavenCentral()
                }
            }
            project("a") {
                apply plugin: "java-library"
            }

            project("b") {
                apply plugin: "scala"
                apply plugin: "java"

                dependencies {
                    implementation ("org.scala-lang:scala3-library_3:3.3.1")
                    implementation project(":a")
                }
            }
            """

        settingsFile << """
            include "a", "b"
            """
    }

    def "multi project compilation with dependent empty java project" (){
        given:
        file("a/src/main/resources/test.txt") << "not relevant"
        file("b/src/main/scala/B.scala") << """
            package b
            class B {
            }
            """
        when:
        succeeds("b:compileScala")
        then:
        noExceptionThrown()
    }
}
