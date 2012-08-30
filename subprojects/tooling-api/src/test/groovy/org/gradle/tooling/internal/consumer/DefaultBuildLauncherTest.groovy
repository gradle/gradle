/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConnection
import org.gradle.tooling.model.Task
import org.gradle.util.ConcurrentSpecification

class DefaultBuildLauncherTest extends ConcurrentSpecification {
    final AsyncConnection protocolConnection = Mock()
    final ConnectionParameters parameters = Mock()
    final DefaultBuildLauncher launcher = new DefaultBuildLauncher(protocolConnection, parameters)

    def buildDelegatesToProtocolConnection() {
        ResultHandler<Void> handler = Mock()

        when:
        launcher.run(handler)

        then:
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def params = args[1]
            assert params.tasks == []
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.progressListener != null
            def wrappedHandler = args[2]
            wrappedHandler.onComplete(null)
        }
        1 * handler.onComplete(null)
        0 * protocolConnection._
        0 * handler._
    }

    def canConfigureTheOperation() {
        Task task1 = task(':task1')
        Task task2 = task(':task2')
        ResultHandler<Void> handler = Mock()

        when:
        launcher.forTasks(task1, task2).run(handler)

        then:
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def params = args[1]
            assert params.tasks == [':task1', ':task2']
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.progressListener != null
            def wrappedHandler = args[2]
            wrappedHandler.onComplete(null)
        }
        1 * handler.onComplete(null)
        0 * protocolConnection._
        0 * handler._
    }

    def canRedirectStandardOutputAndError() {
        ResultHandler<Void> handler = Mock()
        OutputStream stdout = Mock()
        OutputStream stderr = Mock()

        when:
        launcher.standardOutput = stdout
        launcher.standardError = stderr
        launcher.run(handler)

        then:
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def params = args[1]
            assert params.standardOutput == stdout
            assert params.standardError == stderr
            def wrappedHandler = args[2]
            wrappedHandler.onComplete(null)
        }
    }

    def notifiesHandlerOnFailure() {
        ResultHandler<Void> handler = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        launcher.run(handler)

        then:
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def wrappedHandler = args[2]
            wrappedHandler.onFailure(failure)
        }
        1 * handler.onFailure(!null) >> { args ->
            def e = args[0]
            assert e instanceof GradleConnectionException
            assert e.message == 'Could not execute build using <connection>.'
        }
        _ * protocolConnection.displayName >> '<connection>'
        0 * protocolConnection._
        0 * handler._
    }

    def buildBlocksUntilResultReceived() {
        def supplyResult = waitsForAsyncCallback()
        Task task = Mock()

        when:
        supplyResult.start {
            launcher.forTasks(task).run()
        }

        then:
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def handler = args[2]
            supplyResult.callbackLater {
                handler.onComplete(null)
            }
        }
    }

    def buildBlocksUntilFailureReceived() {
        def supplyResult = waitsForAsyncCallback()
        def failure = new RuntimeException()
        Task task = Mock()

        when:
        supplyResult.start {
            launcher.forTasks(task).run()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause == failure
        1 * protocolConnection.run(Void.class, !null, !null) >> { args ->
            def handler = args[2]
            supplyResult.callbackLater {
                handler.onFailure(failure)
            }
        }
    }

    def task(String path) {
        Task task = Mock()
        _ * task.path >> path
        return task
    }
}


