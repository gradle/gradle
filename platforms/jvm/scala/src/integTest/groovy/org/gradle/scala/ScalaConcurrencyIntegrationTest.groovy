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
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue


class ScalaConcurrencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer httpServer = new BlockingHttpServer()

    @Issue("https://github.com/gradle/gradle/issues/14434")
    def "can run tests in parallel with project dependencies"() {
        given:
        String latestScala2 = ScalaCoverage.getLatestSupportedScala2Version()
        httpServer.expectConcurrent(':a:test', ':b:test', ':c:test', ':d:test')
        httpServer.start()

        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        // Configure each project from its own build script rather than from the root
        // via 'allprojects', so the build is compatible with Isolated Projects.
        ['a', 'b', 'c', 'd'].each { project ->
            file("${project}/build.gradle") << """
                plugins {
                    id 'scala'
                }

                tasks.withType(AbstractScalaCompile) {
                    options.fork = true
                }
                ${mavenCentralRepository()}
                dependencies {
                    implementation 'org.scala-lang:scala-library:${latestScala2}'

                    testImplementation 'junit:junit:4.12'
                    testImplementation 'org.scalatest:scalatest_2.13:3.2.0'
                    testImplementation 'org.scalatestplus:junit-4-12_2.13:3.2.0.0'
                }
                tasks.withType(Test) { task ->
                    doLast {
                        ${httpServer.callFromBuild('${task.path}')}
                    }
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
        // Compile everything before the concurrency barrier below. Project 'a' depends on 'b', 'c'
        // and 'd', so ':a:compileScala' cannot start until their jars exist. In the measured build,
        // ':b:test', ':c:test' and ':d:test' block on the barrier holding 3 of the 4 worker leases,
        // leaving ':a' a single lease on which to run two forked Scala compilations before ':a:test'
        // can reach the barrier -- regularly longer than BlockingHttpServer's 120s timeout.
        succeeds(":a:testClasses", ":b:testClasses", ":c:testClasses", ":d:testClasses")

        and:
        // 8 workers, not 4: all four ':test' tasks must be in flight at once, so demanding
        // exactly max-workers leaves zero tolerance for any other lease holder (e.g. ':a:jar').
        // Headroom removes that ceiling; expectConcurrent still asserts all four run concurrently.
        succeeds("build", "--parallel", "--max-workers", "8")
    }
}
