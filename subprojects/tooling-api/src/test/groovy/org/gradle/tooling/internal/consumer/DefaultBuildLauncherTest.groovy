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

import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.gradle.TaskListingLaunchable
import org.gradle.tooling.internal.protocol.InternalLaunchable
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector

class DefaultBuildLauncherTest extends ConcurrentSpec {
    final AsyncConsumerActionExecutor asyncConnection = Mock()
    final ConsumerConnection connection = Mock()
    final ConnectionParameters parameters = Mock()
    final DefaultBuildLauncher launcher = new DefaultBuildLauncher(asyncConnection, parameters)

    def "requests consumer connection run build"() {
        ResultHandler<Void> handler = Mock()
        ResultHandlerVersion1<Void> adaptedHandler

        when:
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onComplete(null)
        }
        1 * connection.run(Void, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == []
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.standardInput == null
            assert params.javaHome == null
            assert params.jvmArguments == null
            assert params.arguments == null
            assert params.progressListener != null
            assert params.cancellationToken != null
            return null
        }
        1 * handler.onComplete(null)
        0 * asyncConnection._
        0 * handler._
    }

    def "can configure the operation"() {
        Task task1 = task(':task1')
        Task task2 = task(':task2')
        ResultHandlerVersion1<Void> adaptedHandler
        ResultHandler<Void> handler = Mock()
        OutputStream stdout = Stub()
        OutputStream stderr = Stub()

        when:
        launcher.standardOutput = stdout
        launcher.standardError = stderr
        launcher.forTasks(task1, task2)
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onComplete(null)
        }
        1 * connection.run(Void, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == [':task1', ':task2']
            assert params.standardOutput == stdout
            assert params.standardError == stderr
            return null
        }
        1 * handler.onComplete(null)
        0 * asyncConnection._
        0 * handler._
    }

    def "can configure task selector build operation for consumer generated selectors"() {
        def ts = Mock(TaskListingLaunchable)
        _ * ts.name >> 'myTask'
        _ * ts.taskNames >> Sets.newTreeSet([':a:myTask', ':b:myTask'])
        ResultHandlerVersion1<Void> adaptedHandler
        ResultHandler<Void> handler = Mock()
        OutputStream stdout = Stub()
        OutputStream stderr = Stub()

        when:
        launcher.standardOutput = stdout
        launcher.standardError = stderr
        launcher.forLaunchables(selector(ts))
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onComplete(null)
        }
        1 * connection.run(Void, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == [':a:myTask', ':b:myTask']
            assert params.standardOutput == stdout
            assert params.standardError == stderr
            return null
        }
        1 * handler.onComplete(null)
        0 * asyncConnection._
        0 * handler._
    }

    def "can configure task selector build operation"() {
        def ts = Mock(InternalLaunchable)
        _ * ts.name >> 'myTask'
        ResultHandlerVersion1<Void> adaptedHandler
        ResultHandler<Void> handler = Mock()
        OutputStream stdout = Stub()
        OutputStream stderr = Stub()

        when:
        launcher.standardOutput = stdout
        launcher.standardError = stderr
        launcher.forLaunchables(selector(ts))
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onComplete(null)
        }
        1 * connection.run(Void, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.launchables == [ts]
            assert params.standardOutput == stdout
            assert params.standardError == stderr
            return null
        }
        1 * handler.onComplete(null)
        0 * asyncConnection._
        0 * handler._
    }

    def "preserves task selectors order in build operation"() {
        def ts1 = Mock(TaskListingLaunchable)
        _ * ts1.name >> 'firstTask'
        _ * ts1.taskNames >> Sets.newTreeSet([':firstTask'])
        def ts2 = Mock(TaskListingLaunchable)
        _ * ts2.name >> 'secondTask'
        _ * ts2.taskNames >> Sets.newTreeSet([':secondTask'])
        def ts3 = Mock(TaskListingLaunchable)
        _ * ts3.name >> 'thirdTask'
        _ * ts3.taskNames >> Sets.newTreeSet([':thirdTask'])
        ResultHandlerVersion1<Void> adaptedHandler
        ResultHandler<Void> handler = Mock()
        OutputStream stdout = Stub()
        OutputStream stderr = Stub()

        when:
        launcher.standardOutput = stdout
        launcher.standardError = stderr
        launcher.forLaunchables(selector(ts1), selector(ts2), selector(ts3))
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onComplete(null)
        }
        1 * connection.run(Void, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == [':firstTask', ':secondTask', ':thirdTask']
            assert params.standardOutput == stdout
            assert params.standardError == stderr
            return null
        }
        1 * handler.onComplete(null)
        0 * asyncConnection._
        0 * handler._
    }

    def "notifies handler on failure"() {
        ResultHandler<Void> handler = Mock()
        ResultHandlerVersion1<Void> adaptedHandler
        RuntimeException failure = new RuntimeException()

        when:
        launcher.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
            adaptedHandler.onFailure(failure)
        }
        1 * handler.onFailure(!null) >> { args ->
            def e = args[0]
            assert e instanceof GradleConnectionException
            assert e.message == 'Could not execute build using <connection>.'
        }
        _ * asyncConnection.displayName >> '<connection>'
        0 * asyncConnection._
        0 * handler._
    }

    def "running build does not block"() {
        ResultHandler<Void> handler = Mock()

        given:
        asyncConnection.run(!null, !null) >> { args ->
            def wrappedHandler = args[1]
            start {
                thread.blockUntil.dispatched
                instant.resultAvailable
                wrappedHandler.onComplete(null)
            }
        }
        handler.onComplete(_) >> {
            instant.resultReceived
        }

        when:
        async {
            launcher.run(handler)
            instant.dispatched
            thread.blockUntil.resultReceived
        }

        then:
        instant.dispatched < instant.resultAvailable
        instant.resultAvailable < instant.resultReceived
    }

    def "run() blocks until build is finished"() {
        given:
        asyncConnection.run(!null, !null) >> { args ->
            def handler = args[1]
            start {
                thread.block()
                instant.resultAvailable
                handler.onComplete(null)
            }
        }

        when:
        operation.runBuild {
            launcher.run()
        }

        then:
        operation.runBuild.end > instant.resultAvailable
    }

    def "run() blocks until build fails"() {
        RuntimeException failure = new RuntimeException()

        given:
        asyncConnection.run(!null, !null) >> { args ->
            def handler = args[1]
            start {
                thread.block()
                instant.failureAvailable
                handler.onFailure(failure)
            }
        }

        when:
        operation.runBuild {
            launcher.run()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.is(failure)

        and:
        operation.runBuild.end > instant.failureAvailable
    }

    def "rejects unknown Launchable"() {
        Launchable task = Mock(Launchable)

        when:
        launcher.forLaunchables(selector(task))

        then:
        def e = thrown(GradleException)
        e != null
    }

    def task(String path) {
        def task = new Object() {
            String getPath() { return path }
        }
        return new FixedBuildIdentifierProvider(id()).applyTo(new ProtocolToModelAdapter().builder(Task)).build(task)
    }

    def selector(def object) {
        return new FixedBuildIdentifierProvider(id()).applyTo(new ProtocolToModelAdapter().builder(TaskSelector)).build(object)
    }

    def id() {
        return new DefaultProjectIdentifier(new DefaultBuildIdentifier(new File("foo")), ":")
    }
}


