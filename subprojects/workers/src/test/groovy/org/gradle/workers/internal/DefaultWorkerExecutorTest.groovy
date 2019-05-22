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

import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.internal.work.ConditionalExecution
import org.gradle.internal.work.ConditionalExecutionQueue
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.UsesNativeServices
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerConfiguration
import org.junit.Rule
import spock.lang.Unroll

@UsesNativeServices
class DefaultWorkerExecutorTest extends Specification {
    @Rule RedirectStdOutAndErr output = new RedirectStdOutAndErr()
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def workerDaemonFactory = Mock(WorkerFactory)
    def inProcessWorkerFactory = Mock(WorkerFactory)
    def noIsolationWorkerFactory = Mock(WorkerFactory)
    def buildOperationWorkerRegistry = Mock(WorkerLeaseRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def asyncWorkTracker = Mock(AsyncWorkTracker)
    def forkOptionsFactory = TestFiles.execFactory(temporaryFolder.testDirectory)
    def workerDirectoryProvider = Stub(WorkerDirectoryProvider) {
        getWorkingDirectory() >> { temporaryFolder.testDirectory }
    }
    def runnable = Mock(Runnable)
    def executionQueueFactory = Mock(WorkerExecutionQueueFactory)
    def executionQueue = Mock(ConditionalExecutionQueue)
    def classLoaderRegistry = Mock(ClassLoaderRegistry)
    def worker = Mock(BuildOperationAwareWorker)
    ConditionalExecution task
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * executionQueueFactory.create() >> executionQueue
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, inProcessWorkerFactory, noIsolationWorkerFactory, forkOptionsFactory, buildOperationWorkerRegistry, buildOperationExecutor, asyncWorkTracker, workerDirectoryProvider, executionQueueFactory, classLoaderRegistry)
    }

    def "worker configuration fork property defaults to AUTO"() {
        given:
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(forkOptionsFactory)

        expect:
        configuration.isolationMode == IsolationMode.AUTO

        when:
        configuration.isolationMode = IsolationMode.PROCESS

        then:
        configuration.isolationMode == IsolationMode.PROCESS

        when:
        configuration.isolationMode = IsolationMode.CLASSLOADER

        then:
        configuration.isolationMode == IsolationMode.CLASSLOADER

        when:
        configuration.isolationMode = IsolationMode.NONE

        then:
        configuration.isolationMode == IsolationMode.NONE

        when:
        configuration.isolationMode = null

        then:
        configuration.isolationMode == IsolationMode.AUTO
    }

    def "can convert javaForkOptions to daemonForkOptions"() {
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(forkOptionsFactory)

        given:
        configuration.forkOptions { options ->
            options.minHeapSize = "128m"
            options.maxHeapSize = "128m"
            options.systemProperty("foo", "bar")
            options.jvmArgs("-foo")
            options.bootstrapClasspath("foo")
            options.debug = true
        }

        when:
        def daemonForkOptions = workerExecutor.getDaemonForkOptions(runnable.class, configuration)

        then:
        daemonForkOptions.javaForkOptions.minHeapSize == "128m"
        daemonForkOptions.javaForkOptions.maxHeapSize == "128m"
        daemonForkOptions.javaForkOptions.allJvmArgs.contains("-Dfoo=bar")
        daemonForkOptions.javaForkOptions.allJvmArgs.contains("-foo")
        daemonForkOptions.javaForkOptions.allJvmArgs.contains("-Xbootclasspath:${temporaryFolder.file('foo')}".toString())
        daemonForkOptions.javaForkOptions.allJvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }

    def "can add to classpath on executor"() {
        given:
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(forkOptionsFactory)
        def foo = new File("/foo")
        configuration.classpath([foo])

        when:
        DaemonForkOptions daemonForkOptions = workerExecutor.getDaemonForkOptions(runnable.class, configuration)

        then:
        daemonForkOptions.classLoaderStructure.spec.classpath.contains(foo.toURI().toURL())
    }

    def "executor executes a given runnable in a daemon"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = IsolationMode.PROCESS
            configuration.params = []
        }

        then:
        1 * buildOperationWorkerRegistry.getCurrentWorkerLease()
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * workerDaemonFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, buildOperation ->
            assert spec.implementationClass == TestRunnable
            return new DefaultWorkResult(true, null)
        }
    }

    def "executor executes a given runnable in-process"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = IsolationMode.CLASSLOADER
            configuration.params = []
        }

        then:
        1 * buildOperationWorkerRegistry.getCurrentWorkerLease()
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * inProcessWorkerFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, workOperation, buildOperation ->
            assert spec.implementationClass == TestRunnable
            return new DefaultWorkResult(true, null)
        }
    }

    def "executor executes a given runnable with no isolation"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = IsolationMode.NONE
            configuration.params = []
        }

        then:
        1 * buildOperationWorkerRegistry.getCurrentWorkerLease()
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * noIsolationWorkerFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, workOperation, buildOperation ->
            assert spec.implementationClass == TestRunnable
            return new DefaultWorkResult(true, null)
        }
    }

    def "cannot set classpath in isolation mode NONE"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = IsolationMode.NONE
            configuration.params = []
            configuration.classpath([new File("foo")])
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker classpath cannot be set when using isolation mode NONE"
    }

    @Unroll
    def "cannot set bootstrap classpath in isolation mode #isolationMode"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = isolationMode
            configuration.params = []
            configuration.forkOptions.bootstrapClasspath new File("foo")
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker bootstrap classpath cannot be set when using isolation mode $isolationMode".toString()

        where:
        isolationMode << [IsolationMode.NONE, IsolationMode.CLASSLOADER]
    }

    @Unroll
    def "cannot set jvm arguments in isolation mode #isolationMode"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = isolationMode
            configuration.params = []
            configuration.forkOptions.jvmArgs "foo"
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker jvm arguments cannot be set when using isolation mode $isolationMode".toString()

        where:
        isolationMode << [IsolationMode.NONE, IsolationMode.CLASSLOADER]
    }

    @Unroll
    def "cannot set system properties in isolation mode #isolationMode"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = isolationMode
            configuration.params = []
            configuration.forkOptions.systemProperty "FOO", "bar"
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker system properties cannot be set when using isolation mode $isolationMode".toString()

        where:
        isolationMode << [IsolationMode.NONE, IsolationMode.CLASSLOADER]
    }

    @Unroll
    def "cannot set maximum heap in isolation mode #isolationMode"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = isolationMode
            configuration.params = []
            configuration.forkOptions.maxHeapSize = "foo"
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker maximum heap size cannot be set when using isolation mode $isolationMode".toString()

        where:
        isolationMode << [IsolationMode.NONE, IsolationMode.CLASSLOADER]
    }

    @Unroll
    def "cannot set minimum heap in isolation mode #isolationMode"() {
        when:
        workerExecutor.submit(TestRunnable.class) { WorkerConfiguration configuration ->
            configuration.isolationMode = isolationMode
            configuration.params = []
            configuration.forkOptions.minHeapSize = "foo"
        }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "The worker minimum heap size cannot be set when using isolation mode $isolationMode".toString()

        where:
        isolationMode << [IsolationMode.NONE, IsolationMode.CLASSLOADER]
    }

    static class TestRunnable implements Runnable {
        @Override
        void run() {
            println "executing"
        }
    }
}
