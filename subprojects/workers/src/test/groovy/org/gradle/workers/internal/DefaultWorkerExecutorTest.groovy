/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import com.google.common.util.concurrent.ListenableFutureTask
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import org.gradle.internal.operations.BuildOperationWorkerRegistry
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.UsesNativeServices
import org.gradle.workers.ForkMode
import org.gradle.workers.WorkerConfiguration
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultWorkerExecutorTest extends Specification {
    @Rule RedirectStdOutAndErr output = new RedirectStdOutAndErr()

    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def workerInProcessFactory = Mock(WorkerDaemonFactory)
    def executorFactory = Mock(ExecutorFactory)
    def buildOperationWorkerRegistry = Mock(BuildOperationWorkerRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def asyncWorkTracker = Mock(AsyncWorkTracker)
    def fileResolver = Mock(FileResolver)
    def factory = Mock(Factory)
    def actionImpl = Mock(Runnable)
    def serverImpl = Mock(WorkerDaemonProtocol)
    def executor = Mock(StoppableExecutor)
    def workerDaemon = Mock(WorkerDaemon)
    ListenableFutureTask task
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * fileResolver.resolveLater(_) >> factory
        _ * fileResolver.resolve(_) >> { files -> files[0] }
        _ * executorFactory.create(_ as String) >> executor
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, workerInProcessFactory, fileResolver, serverImpl.class, executorFactory, buildOperationWorkerRegistry, buildOperationExecutor, asyncWorkTracker)
    }

    def "worker configuration fork property defaults to AUTO"() {
        given:
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(fileResolver)

        expect:
        configuration.forkMode == ForkMode.AUTO

        when:
        configuration.forkMode = ForkMode.ALWAYS

        then:
        configuration.forkMode == ForkMode.ALWAYS

        when:
        configuration.forkMode = ForkMode.NEVER

        then:
        configuration.forkMode == ForkMode.NEVER

        when:
        configuration.forkMode = null

        then:
        configuration.forkMode == ForkMode.AUTO
    }

    def "can convert javaForkOptions to daemonForkOptions"() {
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(fileResolver)

        given:
        configuration.forkOptions { options ->
            options.minHeapSize = "128m"
            options.maxHeapSize = "128m"
            options.systemProperty("foo", "bar")
            options.jvmArgs("-foo")
            options.bootstrapClasspath(new File("/foo"))
            options.debug = true
        }

        when:
        def daemonForkOptions = workerExecutor.getDaemonForkOptions(actionImpl.class, configuration)

        then:
        daemonForkOptions.minHeapSize == "128m"
        daemonForkOptions.maxHeapSize == "128m"
        daemonForkOptions.jvmArgs.contains("-Dfoo=bar")
        daemonForkOptions.jvmArgs.contains("-foo")
        daemonForkOptions.jvmArgs.contains("-Xbootclasspath:${File.separator}foo".toString())
        daemonForkOptions.jvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }

    def "can add to classpath on executor"() {
        given:
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(fileResolver)
        def foo = new File("/foo")
        configuration.classpath([foo])

        when:
        DaemonForkOptions daemonForkOptions = workerExecutor.getDaemonForkOptions(actionImpl.class, configuration)

        then:
        daemonForkOptions.classpath.contains(foo)
    }

    def "executor executes a given runnable in a daemon"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.forkMode = ForkMode.ALWAYS
            configuration.params = []
        }

        then:
        1 * buildOperationWorkerRegistry.getCurrent()
        1 * executor.execute(_ as ListenableFutureTask) >> { args -> task = args[0] }

        when:
        task.run()

        then:
        1 * workerDaemonFactory.getDaemon(_, _, _) >> workerDaemon
        1 * workerDaemon.execute(_, _, _, _) >> { action, spec, workOperation, buildOperation ->
            action.execute(spec)
            return new DefaultWorkResult(true, null)
        }

        and:
        output.stdOut.contains("executing")
    }

    def "executor executes a given runnable in-process"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.forkMode = ForkMode.NEVER
            configuration.params = []
        }

        then:
        1 * buildOperationWorkerRegistry.getCurrent()
        1 * executor.execute(_ as ListenableFutureTask) >> { args -> task = args[0] }

        when:
        task.run()

        then:
        1 * workerInProcessFactory.getDaemon(_, _, _) >> workerDaemon
        1 * workerDaemon.execute(_, _, _, _) >> { action, spec, workOperation, buildOperation ->
            action.execute(spec)
            return new DefaultWorkResult(true, null)
        }

        and:
        output.stdOut.contains("executing")
    }

    static class TestRunnable implements Runnable {
        @Override
        void run() {
            println "executing"
        }
    }
}
