/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

@Requires(IntegTestPreconditions.NotParallelExecutor) // no point, always runs in parallel
public class ParallelProjectExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b", "c", "d"'
        buildFile << """
allprojects {
    tasks.addRule("ping<>") { String name ->
        if (name.startsWith("ping")) {
            tasks.create(name) {
                doLast {
                    URL url = new URL("http://localhost:${blockingServer.port}/" + path)
                    println url.openConnection().getHeaderField('RESPONSE')
                }
            }
        }
    }
}
"""
        executer.withArgument('--parallel')
        executer.withArgument('--max-workers=3') // needs to be set to the maximum number of expectConcurrentExecution() calls
        executer.withArgument('--info')
    }

    def "executes dependency project targets concurrently"() {
        projectDependency from: 'a', to: ['b', 'c', 'd']

        expect:
        blockingServer.expectConcurrent(':b:pingServer', ':c:pingServer', ':d:pingServer')
        blockingServer.expect(':a:pingServer')

        run ':a:pingServer'
    }

    def "executes dependency project targets concurrently where possible"() {

        projectDependency from: 'a', to: ['b', 'c']
        projectDependency from: 'b', to: ['d']
        projectDependency from: 'c', to: ['d']

        expect:
        blockingServer.expect(':d:pingServer')
        blockingServer.expectConcurrent(':b:pingServer', ':c:pingServer')
        blockingServer.expect(':a:pingServer')

        run ':a:pingServer'
    }

    def "project dependency a->[b,c] and both b & c fail"() {
        projectDependency from: 'a', to: ['b', 'c']
        failingBuild 'b'
        failingBuild 'c'

        when:
        blockingServer.expectConcurrent(':b:pingServer', ':c:pingServer')

        fails ':a:pingServer'

        then:
        failure.assertHasCause('b failed')
        failure.assertHasCause('c failed')
    }

    def "tasks are executed when they are ready and not necessarily alphabetically"() {
        buildFile << """
            tasks.getByPath(':b:pingA').dependsOn(':a:pingA')
            tasks.getByPath(':b:pingC').dependsOn([':b:pingA', ':b:pingB'])
        """

        expect:
        //project a and b are both executed even though alphabetically more important task is blocked
        blockingServer.expectConcurrent(':b:pingB', ':a:pingA')
        blockingServer.expect(':b:pingA')
        blockingServer.expect(':b:pingC')

        run 'b:pingC'
    }

    def "finalizer tasks are run in parallel"() {
        buildFile << """
            tasks.getByPath(':c:ping').dependsOn ":a:ping", ":b:ping"
            tasks.getByPath(':d:ping').finalizedBy ":c:ping"
        """

        expect:
        blockingServer.expect(':d:ping')
        blockingServer.expectConcurrent(':a:ping', ':b:ping')
        blockingServer.expect(':c:ping')

        run 'd:ping'
    }

    void 'tasks with should run after ordering rules are preferred when running over an idle worker thread'() {
        buildFile << """
            tasks.getByPath(':a:pingA').shouldRunAfter(':b:pingB')
            tasks.getByPath(':b:pingB').dependsOn(':b:pingA')
        """

        expect:
        blockingServer.expectConcurrent(':a:pingA', ':b:pingA')
        blockingServer.expect(':b:pingB')

        run 'a:pingA', 'b:pingB'
    }

    def projectDependency(def link) {
        def from = link['from']
        def to = link['to']

        def dependencies = to.collect {
            "pingServer.dependsOn(':${it}:pingServer')"
        }.join('\n')

        buildFile << """
project(':$from') {
    ${dependencies}
}
"""
    }

    def failingBuild(def project) {
        def failure = "$project failed"
        buildFile << """
project(':$project') {
    pingServer.doLast {
        throw new RuntimeException('$failure')
    }
}
"""
    }
}
