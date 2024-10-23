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
import org.gradle.internal.Factory
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.internal.work.ConditionalExecutionQueue
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.JavaForkOptionsInternal
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.UsesNativeServices
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutionException
import spock.lang.TempDir

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RETAIN_PROJECT_LOCKS

@UsesNativeServices
class DefaultWorkerExecutorParallelTest extends ConcurrentSpec {
    @TempDir
    public File temporaryFolder
    def workerDaemonFactory = Mock(WorkerFactory)
    def workerInProcessFactory = Mock(WorkerFactory)
    def workerNoIsolationFactory = Mock(WorkerFactory)
    def workerThreadRegistry = Mock(WorkerThreadRegistry)
    def buildOperationRunner = Mock(BuildOperationRunner)
    def asyncWorkerTracker = Mock(AsyncWorkTracker)
    def forkOptionsFactory = new TestForkOptionsFactory(TestFiles.execFactory())
    def objectFactory = Stub(ObjectFactory) {
        fileCollection() >> { TestFiles.fileCollectionFactory().configurableFiles() }
    }
    def workerDirectoryProvider = Stub(WorkerDirectoryProvider) {
        getWorkingDirectory() >> { temporaryFolder }
    }
    def executionQueueFactory = Mock(WorkerExecutionQueueFactory)
    def executionQueue = Mock(ConditionalExecutionQueue)
    def classLoaderStructureProvider = Mock(ClassLoaderStructureProvider)
    def actionExecutionSpecFactory = Mock(ActionExecutionSpecFactory)
    def instantiator = Mock(Instantiator)
    def projectCacheDir = Mock(ProjectCacheDir)
    def classpathTransformer = Mock(CachedClasspathTransformer)
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * executionQueueFactory.create() >> executionQueue
        _ * instantiator.newInstance(DefaultWorkerSpec) >> { args -> new DefaultWorkerSpec() }
        _ * instantiator.newInstance(DefaultClassLoaderWorkerSpec) >> { args -> new DefaultClassLoaderWorkerSpec(objectFactory) }
        _ * instantiator.newInstance(DefaultProcessWorkerSpec, _) >> { args -> new DefaultProcessWorkerSpec(args[1][0], objectFactory) }
        _ * instantiator.newInstance(DefaultWorkerExecutor.DefaultWorkQueue, _, _, _) >> { args -> new DefaultWorkerExecutor.DefaultWorkQueue(args[1][0], args[1][1], args[1][2]) }
        _ * classpathTransformer.copyingTransform(_) >> { args -> args[0] }
        _ * projectCacheDir.getDir() >> temporaryFolder
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, workerInProcessFactory, workerNoIsolationFactory, forkOptionsFactory, workerThreadRegistry, buildOperationRunner, asyncWorkerTracker, workerDirectoryProvider, executionQueueFactory, classLoaderStructureProvider, actionExecutionSpecFactory, instantiator, classpathTransformer, temporaryFolder, projectCacheDir)
        _ * actionExecutionSpecFactory.newIsolatedSpec(_, _, _, _, _) >> Mock(IsolatedParametersActionExecutionSpec)
    }

    def "work can be submitted concurrently using #isolationMode"() {
        when:
        async {
            6.times {
                start {
                    thread.blockUntil.allStarted
                    WorkQueue queue = workerExecutor."${isolationMode}" Actions.doNothing()
                    queue.submit(TestExecution.class, Actions.doNothing())
                }
            }
            instant.allStarted
        }

        then:
        6 * workerThreadRegistry.workerThread >> true
        6 * executionQueue.submit(_)

        where:
        isolationMode << ["noIsolation", "classLoaderIsolation", "processIsolation"]
    }

    def "can wait on results to complete"() {
        when:
        workerExecutor.await()

        then:
        1 * asyncWorkerTracker.waitForCompletion(_, RETAIN_PROJECT_LOCKS)
    }

    def "all errors are thrown when waiting on multiple results"() {
        when:
        workerExecutor.await()

        then:
        1 * asyncWorkerTracker.waitForCompletion(_, RETAIN_PROJECT_LOCKS) >> {
            throw new DefaultMultiCauseException(null, new RuntimeException(), new RuntimeException())
        }

        and:
        def e = thrown(WorkerExecutionException)

        and:
        e.causes.size() == 2
    }

    Factory fileFactory() {
        return Stub(Factory) {
            create() >> Stub(File)
        }
    }

    abstract static class TestExecution implements WorkAction<WorkParameters.None> {
        @Override
        void execute() {

        }
    }

    class TestForkOptionsFactory implements JavaForkOptionsFactory {
        private final JavaForkOptionsFactory delegate

        TestForkOptionsFactory(JavaForkOptionsFactory delegate) {
            this.delegate = delegate
        }

        @Override
        JavaForkOptionsInternal newDecoratedJavaForkOptions() {
            return newJavaForkOptions()
        }

        @Override
        JavaForkOptionsInternal newJavaForkOptions() {
            def forkOptions = delegate.newJavaForkOptions()
            forkOptions.setWorkingDir(temporaryFolder)
            return forkOptions
        }

        @Override
        JavaForkOptionsInternal immutableCopy(JavaForkOptionsInternal options) {
            return delegate.immutableCopy(options)
        }
    }
}
