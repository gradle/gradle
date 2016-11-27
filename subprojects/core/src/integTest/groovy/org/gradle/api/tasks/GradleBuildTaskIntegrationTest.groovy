/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class GradleBuildTaskIntegrationTest extends AbstractIntegrationSpec {
    def "handles properties which are not String when calling GradleBuild"() {
        given:
        buildFile << """
            task buildInBuild(type:GradleBuild) {
                buildFile = 'other.gradle'
                startParameter.searchUpwards = false
                startParameter.projectProperties['foo'] = true // not a String
            }
        """
        file('other.gradle') << 'assert foo==true'

        when:
        run 'buildInBuild'

        then:
        noExceptionThrown()
    }

    def "nested build can use Gradle home directory that is different to outer build"() {
        given:
        def dir = file("other-home")
        buildFile << """
            task otherBuild(type:GradleBuild) {
                buildFile = 'other.gradle'
                startParameter.searchUpwards = false
                startParameter.gradleUserHomeDir = file("${dir.toURI()}")
            }
        """

        file('other.gradle') << '''
println "user home dir: " + gradle.gradleUserHomeDir
println "build script code source: " + getClass().protectionDomain.codeSource.location
'''

        when:
        run 'otherBuild'

        then:
        output.contains("user home dir: $dir")
        output.contains("build script code source: ${dir.toURI()}")
    }

    def "nested build can have buildSrc"() {
        given:
        buildFile << """
            task otherBuild(type:GradleBuild) {
                dir = 'other'
                startParameter.searchUpwards = false
            }
        """
        file('other/buildSrc/src/main/java/Thing.java') << "class Thing { }"
        file('other/build.gradle') << """
            new Thing()
        """

        when:
        run 'otherBuild'

        then:
        // TODO - Fix test fixtures to allow assertions on buildSrc tasks rather than relying on output scraping in tests
        outputContains(":other:buildSrc:assemble")
    }

    @Rule BlockingHttpServer barrier = new BlockingHttpServer()

    def "can run multiple GradleBuild tasks concurrently"() {
        barrier.start()

        given:

        /**
         * Setup a build where a `GradleBuild` task while another `GradleBuild` is currently running another build but has not yet finished running the settings file for that build.
         */

        settingsFile << """
            rootProject.name = 'root'
            include '1', '2'
"""
        buildFile << """
            subprojects {
                task otherBuild(type:GradleBuild) {
                    dir = "\${rootProject.file('subprojects')}"
                    tasks = ['log']
                    startParameter.searchUpwards = false
                }
                otherBuild.doFirst {
                    new URL("http://localhost:${barrier.port}/\${project.name}-started").text
                }
                otherBuild.doLast {
                    new URL("http://localhost:${barrier.port}/\${project.name}-finished").text
                }
            }
            task otherBuild(type:GradleBuild) {
                dir = "main"
                tasks = ['log']
                startParameter.searchUpwards = false
            }
        """
        file('main/settings.gradle') << """
            ${barrier.callFromBuildScript("child-build-started")}
            ${barrier.callFromBuildScript("child-build-finished")}
        """
        file('main/build.gradle') << """
            assert gradle.parent.rootProject.name == 'root'
            task log { }
        """
        file('subprojects/build.gradle') << """
            assert gradle.parent.rootProject.name == 'root'
            task log { }
        """

        barrier.expectConcurrentExecution("child-build-started", "1-started", "2-started")
        barrier.expectConcurrentExecution("child-build-finished", "1-finished", "2-finished")

        when:
        executer.withArgument("--parallel")
        run 'otherBuild'

        then:
        noExceptionThrown()
    }
}
