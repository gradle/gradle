/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/25824")
class CopyDestinationDirectoryIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/a.txt") << "a"
    }

    def "#task can configure the destination lazily through destinationDirectory provider"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("out")
            }
        """

        when:
        run 'copy'

        then:
        file('build/out/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    def "#task destinationDirectory reflects a destination set through into()"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                into layout.buildDirectory.dir("viaInto")
            }
            task checkDestination {
                def actual = copy.destinationDirectory.locationOnly
                def legacy = provider { copy.destinationDir }
                def expected = layout.buildDirectory.dir("viaInto")
                doLast {
                    assert actual.get().asFile == expected.get().asFile
                    assert legacy.get() == expected.get().asFile
                }
            }
        """

        expect:
        run 'checkDestination'

        where:
        task << ['Copy', 'Sync']
    }

    def "#task destinationDir setter is reflected in destinationDirectory provider"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDir = file("\$buildDir/legacy")
            }
            task checkDestination {
                def actual = copy.destinationDirectory.locationOnly
                def expected = layout.buildDirectory.dir("legacy")
                doLast {
                    assert actual.get().asFile == expected.get().asFile
                }
            }
        """

        expect:
        run 'checkDestination'

        where:
        task << ['Copy', 'Sync']
    }

    def "wiring #task destinationDirectory as another task input carries the task dependency"() {
        buildFile """
            task producer(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("produced")
            }
            task consumer(type: Copy) {
                from producer.destinationDirectory
                into layout.buildDirectory.dir("consumed")
            }
        """

        when:
        run 'consumer'

        then:
        result.assertTasksExecuted(':producer', ':consumer')
        file('build/consumed/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly in the test")
    def "#task destinationDirectory survives the configuration cache"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("out")
            }
        """

        when:
        run 'copy', '--configuration-cache'

        then:
        file('build/out/a.txt').text == 'a'

        when:
        file('build/out').deleteDir()
        run 'copy', '--configuration-cache'

        then:
        outputContains('Reusing configuration cache.')
        file('build/out/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }
}
