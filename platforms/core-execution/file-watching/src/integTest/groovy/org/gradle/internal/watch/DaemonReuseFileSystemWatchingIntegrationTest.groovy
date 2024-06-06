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

import com.gradle.develocity.testing.annotations.LocalOnly
import org.gradle.test.fixtures.file.TestFile

@LocalOnly
class DaemonReuseFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def setup() {
        executer.requireDaemon()
    }

    def "can run consecutive unrelated builds in a single daemon without issues"() {
        def firstProject = file("first")
        def secondProject = file("second")

        createSampleProject(firstProject, "change1")
        createSampleProject(secondProject, "change2")

        when:
        succeeds(firstProject, "change1")
        updateSampleFile(firstProject)

        succeeds(secondProject, "change2")
        updateSampleFile(secondProject)

        succeeds(firstProject, "change1")
        updateSampleFile(firstProject)

        then:
        inDirectory(firstProject).withArguments("change1", "--debug").run()
    }

    def "can run consecutive unrelated builds in a single daemon without issues if older build was deleted"() {
        def firstProject = file("first")
        def secondProject = file("second")

        createSampleProject(firstProject, "change1")
        createSampleProject(secondProject, "change2")

        when:
        succeeds(firstProject, "change1")
        updateSampleFile(firstProject)

        firstProject.deleteDir()

        succeeds(secondProject, "change2")
        updateSampleFile(secondProject)

        then:
        succeeds(secondProject, "change2")
        !firstProject.exists()
    }

    private void createSampleProject(TestFile dir, String taskName) {
        dir.file("build.gradle") << sampleTask(taskName)
        dir.file("settings.gradle") << """
            rootProject.name = "${dir.name}"
        """
    }

    private String sampleTask(String name) {
        """
            tasks.register("$name") {
                def output = file("output.txt")
                outputs.file(output)
                outputs.upToDateWhen { false }
                doLast {
                    output.text = Math.random() as String
                }
            }
        """
    }

    private void succeeds(TestFile project, String taskName) {
        // need to have --debug here to catch stacktraces
        withWatchFs().inDirectory(project).withArguments(taskName, "--debug").run()
    }

    private void updateSampleFile(TestFile project) {
        waitForChangesToBePickedUp()
        project.file("output.txt").text = "Changed"
        waitForChangesToBePickedUp()
    }
}
