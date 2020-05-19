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

package org.gradle.api.internal.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class CredentialsProviderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='credentials-provider-test'"
        buildFile << """
            class TaskWithCredentials extends DefaultTask {

                private final Property<PasswordCredentials> credentials = project.objects.property(org.gradle.api.credentials.PasswordCredentials)

                @javax.inject.Inject
                TaskWithCredentials(org.gradle.api.internal.provider.CredentialsProviderFactory credentialsProviderFactory) {
                    credentials.set(credentialsProviderFactory.provideCredentials(PasswordCredentials, 'testCredentials'))
                }

                @Input
                Property<Credentials> getCredentials() {
                    return credentials
                }

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

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
            }
        """

        when:
        fails 'taskWithCredentials'

        then:
        notExecuted('firstTask', 'taskWithCredentials')
        failure.assertHasErrorOutput("Cannot query the value of username and password provider because it has no value available.")
        failure.assertHasErrorOutput("The value of this provider is derived from:")
        failure.assertHasErrorOutput("- Gradle property 'testCredentialsPassword'")
        failure.assertHasErrorOutput("- Gradle property 'testCredentialsUsername'")
    }

    def "missing credentials will fail the build at configuration time when the task needing them is in execution graph"() {
        given:
        buildFile << """
            def firstTask = tasks.register('firstTask') {
            }

            def taskWithCredentials = tasks.register('taskWithCredentials', TaskWithCredentials) {
                dependsOn(firstTask)
            }

            tasks.register('finalTask') {
                dependsOn(taskWithCredentials)
            }
        """

        when:
        fails 'finalTask'

        then:
        notExecuted('firstTask', 'taskWithCredentials', 'finalTask')
        failure.assertHasErrorOutput("Cannot query the value of username and password provider because it has no value available.")
        failure.assertHasErrorOutput("The value of this provider is derived from:")
        failure.assertHasErrorOutput("- Gradle property 'testCredentialsPassword'")
        failure.assertHasErrorOutput("- Gradle property 'testCredentialsUsername'")
    }

    @ToBeFixedForInstantExecution
    def "missing credentials declared as task inputs do not break tasks listing"() {
        when:
        buildFile << """
            tasks.register('lazyTask', TaskWithCredentials) {
            }

            task eagerTask(type: TaskWithCredentials) {
            }
        """

        then:
        succeeds 'tasks', '--all'
        succeeds 'help'
    }
}
