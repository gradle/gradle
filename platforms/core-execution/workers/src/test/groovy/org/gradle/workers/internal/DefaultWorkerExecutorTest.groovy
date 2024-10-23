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
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.internal.Actions
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.internal.work.ConditionalExecution
import org.gradle.internal.work.ConditionalExecutionQueue
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.gradle.util.internal.RedirectStdOutAndErr
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerSpec
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultWorkerExecutorTest extends Specification {
    @Rule
    RedirectStdOutAndErr output = new RedirectStdOutAndErr()
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def workerDaemonFactory = Mock(WorkerFactory)
    def inProcessWorkerFactory = Mock(WorkerFactory)
    def noIsolationWorkerFactory = Mock(WorkerFactory)
    def workerThreadRegistry = Mock(WorkerThreadRegistry)
    def buildOperationRunner = Mock(BuildOperationRunner)
    def asyncWorkTracker = Mock(AsyncWorkTracker)
    def forkOptionsFactory = TestFiles.execFactory(temporaryFolder.testDirectory)
    def objectFactory = Stub(ObjectFactory) {
        fileCollection() >> { TestFiles.fileCollectionFactory().configurableFiles() }
    }
    def workerDirectoryProvider = Stub(WorkerDirectoryProvider) {
        getWorkingDirectory() >> { temporaryFolder.testDirectory }
    }
    def runnable = Mock(Runnable)
    def executionQueueFactory = Mock(WorkerExecutionQueueFactory)
    def executionQueue = Mock(ConditionalExecutionQueue)
    def classLoaderStructureProvider = Mock(ClassLoaderStructureProvider)
    def worker = Mock(BuildOperationAwareWorker)
    def actionExecutionSpecFactory = Mock(ActionExecutionSpecFactory)
    def instantiator = Mock(Instantiator)
    def projectCacheDir = Mock(ProjectCacheDir)
    def classpathTransformer = Mock(CachedClasspathTransformer)
    ConditionalExecution task
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * executionQueueFactory.create() >> executionQueue
        _ * instantiator.newInstance(DefaultWorkerSpec) >> { args -> new DefaultWorkerSpec() }
        _ * instantiator.newInstance(DefaultClassLoaderWorkerSpec) >> { args -> new DefaultClassLoaderWorkerSpec(objectFactory) }
        _ * instantiator.newInstance(DefaultProcessWorkerSpec, _) >> { args -> new DefaultProcessWorkerSpec(args[1][0], objectFactory) }
        _ * instantiator.newInstance(DefaultWorkerExecutor.DefaultWorkQueue, _, _, _) >> { args -> new DefaultWorkerExecutor.DefaultWorkQueue(args[1][0], args[1][1], args[1][2]) }
        _ * classpathTransformer.copyingTransform(_) >> { args -> args[0] }
        _ * projectCacheDir.getDir() >> temporaryFolder.testDirectory
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, inProcessWorkerFactory, noIsolationWorkerFactory, forkOptionsFactory, workerThreadRegistry, buildOperationRunner, asyncWorkTracker, workerDirectoryProvider, executionQueueFactory, classLoaderStructureProvider, actionExecutionSpecFactory, instantiator, classpathTransformer, temporaryFolder.testDirectory, projectCacheDir)
        _ * actionExecutionSpecFactory.newIsolatedSpec(_, _, _, _, _) >> Mock(IsolatedParametersActionExecutionSpec)
    }

    def "can convert javaForkOptions to daemonForkOptions"() {
        WorkerSpec configuration = new DefaultProcessWorkerSpec(forkOptionsFactory.newJavaForkOptions(), objectFactory)

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
        DaemonForkOptions daemonForkOptions = workerExecutor.getWorkerRequirement(runnable.class, configuration, null).forkOptions

        then:
        daemonForkOptions.jvmOptions.minHeapSize == "128m"
        daemonForkOptions.jvmOptions.maxHeapSize == "128m"
        daemonForkOptions.jvmOptions.allJvmArgs.contains("-Dfoo=bar")
        daemonForkOptions.jvmOptions.allJvmArgs.contains("-foo")
        daemonForkOptions.jvmOptions.allJvmArgs.contains("-Xbootclasspath:${temporaryFolder.file('foo')}".toString())
        daemonForkOptions.jvmOptions.allJvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }

    def "can add to classpath on executor"() {
        given:
        WorkerSpec configuration = new DefaultClassLoaderWorkerSpec(objectFactory)
        def foo = temporaryFolder.createFile("foo")
        configuration.classpath.from([foo])

        when:
        IsolatedClassLoaderWorkerRequirement requirement = workerExecutor.getWorkerRequirement(runnable.class, configuration, null)

        then:
        1 * classLoaderStructureProvider.getInProcessClassLoaderStructure(_, _) >> { args -> new HierarchicalClassLoaderStructure(new VisitableURLClassLoader.Spec("test", args[0].collect { it.toURI().toURL() })) }

        and:
        requirement.classLoaderStructure.spec.classpath.contains(foo.toURI().toURL())
    }

    def "executor executes a given work action in a daemon"() {
        when:
        workerExecutor.processIsolation().submit(TestExecutable.class, Actions.doNothing())

        then:
        1 * workerThreadRegistry.workerThread >> true
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * workerDaemonFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, buildOperation ->
            assert spec.implementationClass == TestExecutable.class
            return new DefaultWorkResult(true, null)
        }
    }

    def "executor executes a given runnable in-process"() {
        when:
        workerExecutor.classLoaderIsolation().submit(TestExecutable.class, Actions.doNothing())

        then:
        1 * workerThreadRegistry.workerThread >> true
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * inProcessWorkerFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, workOperation, buildOperation ->
            assert spec.implementationClass == TestExecutable
            return new DefaultWorkResult(true, null)
        }
    }

    def "executor executes a given runnable with no isolation"() {
        when:
        workerExecutor.noIsolation().submit(TestExecutable.class, Actions.doNothing())

        then:
        1 * workerThreadRegistry.workerThread >> true
        1 * executionQueue.submit(_) >> { args -> task = args[0] }

        when:
        task.getExecution().run()

        then:
        1 * noIsolationWorkerFactory.getWorker(_) >> worker
        1 * worker.execute(_, _) >> { spec, workOperation, buildOperation ->
            assert spec.implementationClass == TestExecutable
            return new DefaultWorkResult(true, null)
        }
    }

    abstract static class TestExecutable implements WorkAction<WorkParameters.None> {
        @Override
        void execute() {
            println "executing"
        }
    }
}
