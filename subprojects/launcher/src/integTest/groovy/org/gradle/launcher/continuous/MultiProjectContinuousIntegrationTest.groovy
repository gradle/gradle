/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.fixtures.condition.TestPrecondition
import org.gradle.test.fixtures.condition.UnitTestPreconditions
import spock.lang.Ignore

class MultiProjectContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def upstreamSource, downstreamSource

    def setup() {
        executer.noExtraLogging()
        settingsFile << "include 'upstream', 'downstream'"
        buildFile << """
            subprojects {
                apply plugin: 'java-library'
                ${mavenCentralRepository()}
            }

            project(':downstream') {
                dependencies {
                    implementation project(":upstream")
                }
            }
        """

        upstreamSource = file("upstream/src/main/java/Upstream.java") << "class Upstream {}"
        downstreamSource = file("downstream/src/main/java/Downstream.java").createFile() << "class Downstream extends Upstream {}"

        // MacOS builds with Intel machines can be too fast on CI and
        // changes generated during build are picked up only after the build which retriggers the build
        // Fix for issue: https://github.com/gradle/gradle-private/issues/3728
        if (TestPrecondition.doSatisfies(UnitTestPreconditions.MacOs) && TestPrecondition.notSatisfies(UnitTestPreconditions.MacOsM1)) {
            def quietPeriod = 250
            waitAtEndOfBuildForQuietPeriod(quietPeriod)
        }
    }

    def "changes to upstream project triggers build of downstream"() {
        expect:
        succeeds "build"
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"

        when:
        upstreamSource.text = "class Upstream { int change = 1; }"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"

        when:
        downstreamSource.text = "class Downstream extends Upstream { int change = 1; }"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped ":downstream:compileJava"
        skipped ":upstream:compileJava"

        when:
        upstreamSource.text = "class Upstream {"

        then:
        buildTriggeredAndFailed()

        when:
        downstreamSource.text = "class Downstream extends Upstream { int change = 11; }"

        then:
        noBuildTriggered()

        when:
        downstreamSource.text = "class Downstream extends Upstream{}"
        upstreamSource.text = "class Upstream {}"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"
    }

    @Ignore("This goes into a continuous loop since .gradle files change")
    def "can specify root directory of multi project build as a task input; changes are respected"() {
        given:
        buildFile << """
            allprojects {
                task before {
                    def outputFile = new File(buildDir, "output.txt")
                    outputs.file outputFile
                    outputs.upToDateWhen { false }

                    doLast {
                        outputFile.parentFile.mkdirs()
                        outputFile.text = "OK"
                    }
                }

                task a {
                    dependsOn before
                    inputs.dir rootDir
                    doLast {
                    }
                }
            }
        """

        expect:
        succeeds "a"
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")

        when:
        file("A").text = "A"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")

        expect:
        succeeds(":downstream:a")
        executedAndNotSkipped(":downstream:a")
        notExecuted(":a", ":upstream:a")

        when:
        file("B").text = "B"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":downstream:a")
    }

    // here to put more stress on parallel execution
    def "reasonable sized multi-project"() {
        given:
        def extraProjectNames = (0..100).collect { "project$it" }
        extraProjectNames.each {
            settingsFile << "\n include '$it' \n"
            buildFile << "\n project(':$it') { dependencies { implementation project(':upstream') } } \n"
            file("${it}/src/main/java/Thing${it}.java").createFile() << """
                class Thing${it} extends Upstream {}
            """
        }

        String[] extraCompileTasks = extraProjectNames.collect { ":$it:compileJava" }*.toString().toArray()
        def anExtraProjectName = extraProjectNames[(extraProjectNames.size() / 2).toInteger()]

        expect:
        succeeds("build")

        when:
        downstreamSource.text = "class Downstream extends Upstream { int change = 1; }"

        then:
        buildTriggeredAndSucceeded()
        skipped extraCompileTasks

        when:
        upstreamSource.text = "class Upstream { int change = 1; }"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped extraCompileTasks

        when:
        file("${anExtraProjectName}/src/main/java/Thing.java").text = "class Thing extends Upstream{ int change = 1; }"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped ":$anExtraProjectName:compileJava"
    }
}
