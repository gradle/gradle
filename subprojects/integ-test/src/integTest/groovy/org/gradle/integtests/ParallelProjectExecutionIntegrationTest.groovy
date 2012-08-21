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
import org.gradle.integtests.fixtures.BlockingHttpServer

public class ParallelProjectExecutionIntegrationTest extends AbstractIntegrationSpec {

    def blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b", "c", "d"'
        buildFile << """
allprojects {
    task pingServer << {
        URL url = new URL("http://localhost:${blockingServer.port}/" + project.path)
        println url.openConnection().getHeaderField('RESPONSE')
    }
}
"""
        executer.withArgument('--info')

    }

    def "executes dependent project targets concurrently"() {
        projectDependency from: 'c', to: ['a', 'b']
        projectDependency from: 'd', to: ['c']

        expect:
        blockingServer.expectConcurrentExecution(':a', ':b')
        blockingServer.expectConcurrentExecution(':c')
        blockingServer.expectConcurrentExecution(':d')

        run ':d:pingServer'
    }

    def "executes dependency project targets concurrently2"() {

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
        failure.assertHasCause 'b failed'
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
