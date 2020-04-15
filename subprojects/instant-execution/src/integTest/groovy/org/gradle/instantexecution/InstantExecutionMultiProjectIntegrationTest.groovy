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

package org.gradle.instantexecution

class InstantExecutionMultiProjectIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "reuses cache for absolute task invocation from subproject dir across dirs"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        buildScript """
            task ok
        """
        def a = createDir('a')
        def b = createDir('b')
        def instantExecution = newInstantExecutionFixture()

        when:
        inDirectory a
        instantRun ':ok'

        then:
        instantExecution.assertStateStored()

        when:
        inDirectory b
        instantRun ':ok'

        then:
        instantExecution.assertStateLoaded()

        when:
        inDirectory a
        instantRun ':ok'

        then:
        instantExecution.assertStateLoaded()
    }

    def "reuses cache for relative task invocation from subproject dir"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        buildScript """
            allprojects {
                task ok
            }
        """
        def a = createDir('a')
        def b = createDir('b')
        def instantExecution = newInstantExecutionFixture()

        when:
        inDirectory testDirectory
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':ok', ':a:ok', ':b:ok')
        instantExecution.assertStateStored()

        when:
        inDirectory a
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':a:ok')
        instantExecution.assertStateStored()

        when:
        inDirectory b
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':b:ok')
        instantExecution.assertStateStored()

        when:
        inDirectory a
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':a:ok')
        instantExecution.assertStateLoaded()

        when:
        inDirectory b
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':b:ok')
        instantExecution.assertStateLoaded()

        when:
        inDirectory testDirectory
        instantRun 'ok'

        then:
        result.assertTasksExecuted(':ok', ':a:ok', ':b:ok')
        instantExecution.assertStateLoaded()
    }
}
