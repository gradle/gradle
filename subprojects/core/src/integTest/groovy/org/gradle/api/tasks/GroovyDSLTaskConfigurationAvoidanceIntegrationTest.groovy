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

class GroovyDSLTaskConfigurationAvoidanceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            tasks.help { t ->
                println('help task created')
            }
        """
    }

    def "task is not eagerly created"() {
        expect:
        succeeds('projects')
        outputDoesNotContain('help task created')
    }

    def "task is not eagerly created when accessed as property"() {
        buildFile << """
            def helpTask = tasks.help
            assert helpTask instanceof TaskProvider
        """

        expect:
        succeeds('projects')
        outputDoesNotContain('help task created')
    }

    def "task is created on demand when task property is accessed"() {
        buildFile << """
            tasks.help.taskPath = 'projects'
        """

        expect:
        succeeds('projects')
        outputContains('help task created')
    }

    def "can access task property through provider if task was already created"() {
        buildFile << """
            tasks.help.get() // realize the task
            tasks.help.taskPath = 'projects'
        """

        expect:
        succeeds('projects')
        outputContains('help task created')
    }

    def "task is not eagerly created when a detail of the provider is accessed"() {
        buildFile << """
            tasks.help.name
        """

        expect:
        succeeds('projects')
        outputDoesNotContain('help task created')
    }

    def "task provider can be stored and used"() {
        buildFile << """
            def helpTaskProvider = tasks.help
            assert helpTaskProvider instanceof TaskProvider
            helpTaskProvider {
                println("help task configured")
            }
        """

        when:
        succeeds('projects')

        then:
        outputDoesNotContain('help task created')
        outputDoesNotContain('help task configured')

        and:
        succeeds('help')

        then:
        outputContains('help task created\nhelp task configured')
    }

}
