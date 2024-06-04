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

@LocalOnly
class DaemonReuseFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def setup() {
        executer.requireDaemon()
    }

    def "can run consecutive builds in a single daemon without issues"() {
        buildFile << """
            tasks.register("change") {
                def output = file("output.txt")
                outputs.file(output)
                outputs.upToDateWhen { false }
                doLast {
                    output.text = Math.random() as String
                }
            }
        """

        when:
        withWatchFs().run("change", "--debug")
        waitForChangesToBePickedUp()
        file("output.txt").text = "Changed"
        waitForChangesToBePickedUp()
        withWatchFs().run("change", "--debug")
        waitForChangesToBePickedUp()

        then:
        succeeds("change")
    }

    def "can run consecutive unrelated builds in a single daemon without issues"() {
        def firstProject = file("first")
        def secondProject = file("second")

        file(firstProject.name, "build.gradle") << """
            tasks.register("change1") {
                def output = file("output.txt")
                outputs.file(output)
                outputs.upToDateWhen { false }
                doLast {
                    output.text = Math.random() as String
                }
            }
        """
        file(firstProject.name, "settings.gradle") << """
            rootProject.name = "first"
        """

        file(secondProject.name, "build.gradle") << """
            tasks.register("change2") {
                def output = file("output.txt")
                outputs.file(output)
                outputs.upToDateWhen { false }
                doLast {
                    output.text = Math.random() as String
                }
            }
        """
        file(secondProject.name, "settings.gradle") << """
            rootProject.name = "second"
        """

        when:
        withWatchFs().inDirectory(firstProject).withArguments("change1").run()
        waitForChangesToBePickedUp()
        file(firstProject, "output.txt").text = "Changed"
        waitForChangesToBePickedUp()

        withWatchFs().inDirectory(secondProject).withArguments("change2").run()
        waitForChangesToBePickedUp()
        file(secondProject, "output.txt").text = "Changed"
        waitForChangesToBePickedUp()

        withWatchFs().inDirectory(firstProject).withArguments("change1").run()
        waitForChangesToBePickedUp()
        file(firstProject, "output.txt").text = "Changed"
        waitForChangesToBePickedUp()

        then:
        inDirectory(firstProject).withArguments("change1").run()
    }
}
