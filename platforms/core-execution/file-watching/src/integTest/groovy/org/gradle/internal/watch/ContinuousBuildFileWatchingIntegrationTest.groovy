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

package org.gradle.internal.watch

import org.gradle.testdistribution.LocalOnly
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.FileSystemWatchingHelper
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@LocalOnly
@Requires(value = TestExecutionPreconditions.NotNoDaemonExecutor, reason = "There is no shared state without a daemon")
class ContinuousBuildFileWatchingIntegrationTest extends AbstractContinuousIntegrationTest implements FileSystemWatchingFixture {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    def "file system watching picks up changes causing a continuous build to rebuild"() {
        given:
        // Do not drop the VFS in the first build, since there is only one continuous build invocation.
        // FileSystemWatchingFixture automatically sets the argument for the first build.
        executer.withArgument(FileSystemWatchingHelper.getDropVfsArgument(false))
        executer.beforeExecute {
            withWatchFs()
        }

        def numberOfFilesInVfs = 4 // source file, class file, JAR manifest, JAR file
        def vfsLogs = enableVerboseVfsLogs()

        when:
        buildFile << """
            plugins {
                id('java')
            }
        """
        def sourceFile = file("src/main/java/Thing.java")
        sourceFile << "class Thing {}"

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs

        when:
        sourceFile.text = "class Thing { public void doStuff() {} }"

        then:
        buildTriggeredAndSucceeded()
        vfsLogs.getRetainedFilesSinceLastBuild() >= numberOfFilesInVfs - 1
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs
        executedAndNotSkipped(":compileJava")
        executed(":build")
    }

    def "arming the watch probe does not retrigger a continuous build"() {
        given:
        executer.withArgument(FileSystemWatchingHelper.getDropVfsArgument(false))
        executer.beforeExecute {
            withWatchFs()
            // Outside the project directory, which is declared as an input below: <projectDir>/.gradle
            // then holds nothing but the watch probe, and cache writes are not input changes either.
            withArgument("--project-cache-dir")
            withArgument(file("../project-cache").absolutePath)
        }

        def input = file("input.txt")
        input.text = "original"

        // The whole project directory is an input, unfiltered, so Gradle's own probe artifacts under
        // .gradle count as inputs. The task writes nothing under the project directory, so anything
        // that changes there is Gradle's doing.
        buildFile << """
            tasks.register("checkInput") {
                inputs.dir(projectDir)
                outputs.upToDateWhen { false }
                doLast {
                    println "input is " + file("input.txt").text
                }
            }
        """

        when:
        succeeds("checkInput")

        then:
        outputContains("input is original")

        when: "a real change arrives, and the build it triggers re-arms the probe at its start"
        input.text = "changed"

        then: "the build still reacts, so the filter did not silence everything"
        buildTriggeredAndSucceeded()
        outputContains("input is changed")


        and: """no build follows. The first build of a session cannot re-arm - watchRegistry is read
                before the lock and is null there - so this window, after a build that did rotate the
                probe, is the only one that covers arming. The window is wider than the default because
                the notification it must not see can take seconds to arrive here, and a window shorter
                than that latency passes whether the filter works or not. It stays an ambiguous
                assertion, as the fixture's own TODO at AbstractContinuousIntegrationTest:284 says, so
                the results check below carries what it cannot."""
        def buildsBeforeTheWindow = results.size()
        noBuildTriggered(15)

        and: "and none completed inside it either, which the output of the last one would hide"
        results.size() == buildsBeforeTheWindow
    }
}
