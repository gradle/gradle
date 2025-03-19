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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Ignore

@Ignore("No longer works with parallel execution of included builds. See gradle/composite-builds#114")
class CompositeBuildDestroyablesIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version = "2.0"

                    repositories {
                        maven { url = "${mavenRepo.uri}" }
                    }
                }
            """
        }
        includedBuilds << buildB
    }

    def "clean build and build clean work reliably with composite build"() {
        given:
        dependency "org.test:buildB:1.0"
        buildA.buildFile << """
            clean {
                dependsOn gradle.includedBuild('buildB').task(':clean')
            }
        """

        when:
        args "--parallel"
        execute(buildA, "clean", "build")

        then:
        result.assertTaskOrder(':buildB:clean', ':buildB:compileJava', ':clean', ':compileJava')

        when:
        args "--parallel"
        execute(buildA, "build", "clean")

        then:
        result.assertTaskOrder(':buildB:compileJava', ':compileJava', ':buildB:clean', ':clean')
    }
}
