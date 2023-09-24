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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers
import spock.lang.Issue

class CredentialsProviderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='credentials-provider-test'"
        buildFile """
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
        buildFile """
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

    @Issue("https://github.com/gradle/gradle/issues/13770")
    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "missing credentials error messages can be assembled in parallel execution (#credentialsType)"() {
        def buildScript = """
            tasks.register('executionCredentials') {
                def providers = project.providers
                doLast {
                    providers.credentials($credentialsType, 'test').get()
                }
            }
        """
        file('submodule1/build.gradle') << buildScript
        file('submodule2/build.gradle') << buildScript
        file('submodule3/build.gradle') << buildScript
        file('submodule4/build.gradle') << buildScript
        settingsFile << """
            rootProject.name="test"
            include("submodule1", "submodule2", "submodule3", "submodule4")
        """
        buildFile << ""

        expect:
        for (int i = 0; i < 10; i++) {
            args '--parallel', '--continue'
            fails 'executionCredentials'

            failure.assertNotOutput("ConcurrentModificationException")
            failure.assertThatCause(CoreMatchers.equalTo(errorMessage))
            failure.assertHasFailures(4)
        }

        where:
        credentialsType         | errorMessage
        'AwsCredentials'        | "The following Gradle properties are missing for 'test' credentials:\n  - testAccessKey\n  - testSecretKey"
        'PasswordCredentials'   | "The following Gradle properties are missing for 'test' credentials:\n  - testUsername\n  - testPassword"
        'HttpHeaderCredentials' | "The following Gradle properties are missing for 'test' credentials:\n  - testAuthHeaderName\n  - testAuthHeaderValue"
    }

    @UnsupportedWithConfigurationCache(because = "test checks behavior with and without configuration cache")
    def "credential values are not cached between executions"() {
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

    @NotYetImplemented
    @UnsupportedWithConfigurationCache(because = "test checks behavior with configuration cache")
    def "credential values are not stored in configuration cache`"() {
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
        args '-PtestCredentialsUsername=user-value', '-PtestCredentialsPassword=password-value', '--configuration-cache'

        then:
        succeeds 'finalTask'
        def configurationCacheDirs = file('.gradle/configuration-cache/').listFiles().findAll { it.isDirectory() }
        configurationCacheDirs.size() == 1
        def configurationCacheFiles = configurationCacheDirs[0].listFiles()
        configurationCacheFiles.size() == 2
        configurationCacheFiles.each {
            def content = it.getText()
            assert !content.contains('user-value')
            assert !content.contains('password-value')
        }
    }
}
