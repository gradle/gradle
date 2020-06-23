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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class CredentialsProviderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='credentials-provider-test'"
        buildFile << """
            abstract class TaskWithCredentials extends DefaultTask {

                @Input
                abstract Property<Credentials> getCredentials()

                @TaskAction
                void run() {
                    println 'running TaskWithCredentials'
                    println 'username: ' + credentials.get().getUsername()
                    println 'password: ' + credentials.get().getPassword()
                }
            }
        """
    }

    def "credentials are supplied when present on command line"() {
        given:
        buildFile << """
            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }
        """

        when:
        args '-PtestCredentialsUsername=user', '-PtestCredentialsPassword=secret'
        succeeds 'taskWithCredentials'

        then:
        outputContains('running TaskWithCredentials')
        outputContains('username: user')
        outputContains('password: secret')
    }

    def "can execute a task when credentials are missing for task not in execution graph"() {
        when:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }
        """

        then:
        succeeds 'firstTask'
    }

    def "missing credentials will fail the build at configuration time when the task needing them is executed directly"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }
        """

        when:
        fails 'taskWithCredentials'

        then:
        notExecuted(':firstTask', ':taskWithCredentials')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'testCredentials' credentials:")
        failure.assertHasErrorOutput("- testCredentialsUsername")
        failure.assertHasErrorOutput("- testCredentialsPassword")
    }

    def "missing credentials will fail the build at configuration time when the task needing them is in execution graph"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }

            tasks.register('finalTask') {
                dependsOn(taskWithCredentials)
            }
        """

        when:
        fails 'finalTask'

        then:
        notExecuted(':firstTask', ':taskWithCredentials', ':finalTask')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'testCredentials' credentials:")
        failure.assertHasErrorOutput("- testCredentialsUsername")
        failure.assertHasErrorOutput("- testCredentialsPassword")
    }

    @ToBeFixedForInstantExecution
    def "missing credentials declared as task inputs do not break tasks listing"() {
        when:
        buildFile << """
            tasks.register('lazyTask', TaskWithCredentials) {
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }

            task eagerTask(type: TaskWithCredentials) {
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }
        """

        then:
        succeeds 'tasks', '--all'
        succeeds 'help'
    }

    def "multiple missing credentials for different tasks in graph are reported in same failure"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
                credentials.set(providers.credentials(PasswordCredentials, 'someCredentials'))
            }

            def anotherTaskWithCredentials = tasks.register('anotherTaskWithCredentials', TaskWithCredentials) {
                dependsOn(taskWithCredentials)
                credentials.set(providers.credentials(PasswordCredentials, 'someOtherCredentials'))
            }

            tasks.register('finalTask') {
                dependsOn(anotherTaskWithCredentials)
            }
        """

        when:
        fails 'finalTask'

        then:
        notExecuted(':firstTask', ':taskWithCredentials', ':finalTask')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'someCredentials' credentials:")
        failure.assertHasErrorOutput("- someCredentialsUsername")
        failure.assertHasErrorOutput("- someCredentialsPassword")
        failure.assertHasCause("The following Gradle properties are missing for 'someOtherCredentials' credentials:")
        failure.assertHasErrorOutput("- someOtherCredentialsUsername")
        failure.assertHasErrorOutput("- someOtherCredentialsPassword")
    }

    def "programmatically registered inputs with credentials provider are evaluated before execution"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            tasks.register('taskWithCredentials') {
                dependsOn(firstTask)
                inputs.property('credentials', providers.credentials(PasswordCredentials, 'testCredentials'))
            }
        """

        when:
        fails 'taskWithCredentials'

        then:
        notExecuted(':firstTask', ':taskWithCredentials')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'testCredentials' credentials:")
        failure.assertHasErrorOutput("- testCredentialsUsername")
        failure.assertHasErrorOutput("- testCredentialsPassword")
    }

    // Should be ignored for instant execution - this test checks behavior with and without configuration cache
    @ToBeFixedForInstantExecution
    def "credentials are not cached"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
                credentials.set(providers.credentials(PasswordCredentials, 'testCredentials'))
            }

            tasks.register('finalTask') {
                dependsOn(taskWithCredentials)
            }
        """

        when:
        args '-PtestCredentialsUsername=user', '-PtestCredentialsPassword=secret', '--configuration-cache'
        succeeds 'finalTask'

        then:
        args '--configuration-cache'
        fails 'finalTask'
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
    }
}
