/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.announce

import org.gradle.integtests.fixtures.WellBehavedPluginTest

class BuildAnnouncementsPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "tasks"
    }

    def "does not blow up when a local notification mechanism is not available"() {
        buildFile << """
apply plugin: 'build-announcements'
"""

        expect:
        succeeds 'tasks'
    }

    def "does not blow up in headless mode when a local notification mechanism is not available"() {
        buildFile << """
apply plugin: 'build-announcements'
"""

        given:
        executer.withArgument("-Djava.awt.headless=false")

        expect:
        succeeds 'tasks'
    }

    def "can use custom announcer to receive announcements"() {
        buildFile << """
apply plugin: 'build-announcements'

task a
task b(dependsOn: a)

announce.local = ({title, message -> println "[\$title][\$message]" } as Announcer)
"""

        when:
        run 'b'

        then:
        output.contains("[Build successful][2 tasks executed]")
    }

    def "announces build failure"() {
        buildFile << """
apply plugin: 'build-announcements'

task broken {
    doLast {
        throw new RuntimeException()
    }
}

announce.local = ({title, message -> println "[\$title][\$message]" } as Announcer)
"""

        when:
        fails 'broken'

        then:
        output.contains("[Build failed][task ':broken' failed\n1 task executed]")
    }

    def "announces multiple build failures"() {
        buildFile << """
apply plugin: 'build-announcements'

task broken1 {
    doLast {
        throw new RuntimeException()
    }
}
task broken2 {
    doLast {
        throw new RuntimeException()
    }
}

announce.local = ({title, message -> println "[\$title][\$message]" } as Announcer)
"""

        when:
        executer.withArguments("--continue")
        fails 'broken1', 'broken2'

        then:
        output.contains("[task ':broken1' failed][1 task failed]")
        output.contains("[Build failed][2 tasks failed\n2 tasks executed]")
    }
}
