/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.integtests.tooling.r21

import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.gradle.GradleBuild

class CancellationCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    def "early cancel stops the build before beginning"() {
        buildFile << """
throw new GradleException("should not run")
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "can cancel build after completion"() {
        buildFile << """
task thing
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('thing')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            resultHandler.finished()
            cancel.cancel()
        }

        then:
        noExceptionThrown()
    }

    def "early cancel stops model retrieval before beginning"() {
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.model(GradleBuild)
            build.withCancellationToken(cancel.token())
            build.get(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "can cancel build after model retrieval"() {
        buildFile << """
task thing
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.model(GradleBuild)
            build.withCancellationToken(cancel.token())
            build.get(resultHandler)
            resultHandler.finished()
            cancel.cancel()
        }

        then:
        noExceptionThrown()
    }

    def "early cancel stops the action before beginning"() {
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.action(new HangingBuildAction())
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }
}
