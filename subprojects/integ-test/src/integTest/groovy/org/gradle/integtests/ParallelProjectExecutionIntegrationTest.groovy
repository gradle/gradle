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

package org.gradle.integtests;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

public class ParallelProjectExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b", "c", "d"'
        buildFile << """
assert gradle.startParameter.parallelThreadCount != 0
allprojects {
    tasks.addRule("ping<>") { String name ->
        if (name.startsWith("ping")) {
            tasks.create(name) {
                doLast {
                    URL url = new URL("http://localhost:${blockingServer.port}/" + project.path)
                    println url.openConnection().getHeaderField('RESPONSE')
                }
            }
        }
    }
}
"""
        executer.withArgument('--parallel')
        executer.withArgument('--info')
    }

    def "executes dependency project targets concurrently"() {

        projectDependency from: 'a', to: ['b', 'c', 'd']

        expect:
        blockingServer.expectConcurrentExecution(':b', ':c', ':d')
        blockingServer.expectConcurrentExecution(':a')

        run ':a:pingServer'
    }

    def "executes dependency project targets concurrently where possible"() {

        projectDependency from: 'a', to: ['b', 'c']
        projectDependency from: 'b', to: ['d']
        projectDependency from: 'c', to: ['d']

        expect:
        blockingServer.expectConcurrentExecution(':d')
        blockingServer.expectConcurrentExecution(':b', ':c')
        blockingServer.expectConcurrentExecution(':a')

        run ':a:pingServer'
    }

    def "project dependency a->[b,c] and both b & c fail"() {
        projectDependency from: 'a', to: ['b', 'c']
        failingBuild 'b'
        failingBuild 'c'

        when:
        blockingServer.expectConcurrentExecution(':b', ':c')

        fails ':a:pingServer'

        then:
        failure.error =~ 'b failed'
        failure.error =~ 'c failed'
    }

    def "tasks are executed when they are ready and not necessarily alphabetically"() {
        buildFile << """
            tasks.getByPath(':b:pingA').dependsOn(':a:pingA')
            tasks.getByPath(':b:pingC').dependsOn([':b:pingA', ':b:pingB'])
        """

        expect:
        //project a and b are both executed even though alphabetically more important task is blocked
        blockingServer.expectConcurrentExecution(':b', ':a')
        blockingServer.expectConcurrentExecution(':b')
        blockingServer.expectConcurrentExecution(':b')

        run 'b:pingC'
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
