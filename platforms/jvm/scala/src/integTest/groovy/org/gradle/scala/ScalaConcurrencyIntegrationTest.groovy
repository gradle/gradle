/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue


class ScalaConcurrencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer httpServer = new BlockingHttpServer()

    @Issue("https://github.com/gradle/gradle/issues/14434")
    def "can run tests in parallel with project dependencies"() {
        given:
        httpServer.expectConcurrent(':a:test', ':b:test', ':c:test', ':d:test')
        httpServer.start()

        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        buildFile << """
            allprojects {
                tasks.withType(AbstractScalaCompile) {
                    options.fork = true
                }
                ${mavenCentralRepository()}
                plugins.withId("scala") {
                    dependencies {
                        implementation 'org.scala-lang:scala-library:2.13.12'

                        testImplementation 'junit:junit:4.12'
                        testImplementation 'org.scalatest:scalatest_2.13:3.2.0'
                        testImplementation 'org.scalatestplus:junit-4-12_2.13:3.2.0.0'
                    }
                }
                tasks.withType(Test) { task ->
                    doLast {
                        ${httpServer.callFromBuild('${task.path}')}
                    }
                }
            }
        """
        ['a', 'b', 'c', 'd'].each { project ->
            file("${project}/build.gradle") << """
                plugins {
                    id 'scala'
                }
            """
            file("${project}/src/main/scala/${project}/${project.toUpperCase()}.scala") << """
                package ${project}
                trait ${project.toUpperCase()}
            """
            file("${project}/src/test/scala/${project}/${project.toUpperCase()}Test.scala") << """
                package ${project}
                import org.scalatest.funsuite.AnyFunSuite
                import org.junit.runner.RunWith
                import org.scalatestplus.junit.JUnitRunner

                @RunWith(classOf[JUnitRunner])
                class ${project.toUpperCase()}Test extends AnyFunSuite {
                  test("always true") {
                      assert(true)
                  }
                }
            """
        }
        file("a/build.gradle") << """
            dependencies {
              implementation(project(":b"))
              implementation(project(":c"))
              implementation(project(":d"))
            }
        """

        expect:
        succeeds("build", "--parallel", "--max-workers", "4")
        true
    }
}
