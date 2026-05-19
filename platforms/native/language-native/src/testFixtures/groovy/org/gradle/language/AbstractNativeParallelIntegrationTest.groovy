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

package org.gradle.language

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractNativeParallelIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        executer.withArgument("--max-workers=4")
    }

    def cleanup() {
        server.stop()
    }

    void assertTaskIsParallel(String taskName) {
        def task = buildOperations.first(ExecuteTaskBuildOperationType, new Spec<BuildOperationRecord>() {
            @Override
            boolean isSatisfiedBy(BuildOperationRecord record) {
                return record.displayName == "Task :${taskName}"
            }
        })

        assert task != null
        def concurrentTasks = buildOperations.getOperationsConcurrentWith(ExecuteTaskBuildOperationType, task)
        assert concurrentTasks.find { it.displayName == "Task :parallelTask" }
    }

    private void setupParallelTaskAndExpectations(String taskName) {
        server.start()
        server.expectConcurrent("operationsStarted", "parallelTaskStarted")
        server.expectConcurrent("operationsFinished", "parallelTaskFinished")

        buildFile << """
            def beforeOperations = { ${server.callFromBuild("operationsStarted")} }
            def afterOperations = { ${server.callFromBuild("operationsFinished")} }

            task parallelTask {
                dependsOn { tasks.${taskName}.taskDependencies }
                doLast {
                    ${server.callFromBuild("parallelTaskStarted")}
                    println "parallel task"
                    ${server.callFromBuild("parallelTaskFinished")}
                }
            }
        """
    }

    def createTaskThatRunsInParallelUsingCustomToolchainWith(String taskName) {
        setupParallelTaskAndExpectations(taskName)

        buildFile << """
            ${callbackToolChain}

            tasks.matching { it.name == '${taskName}' }.all {
                def originalToolChain
                doFirst {
                    originalToolChain = toolChain.get()
                    toolChain = new CallbackToolChain(originalToolChain, beforeOperations, afterOperations)
                }
                doLast {
                    toolChain.get().undecorateToolProviders()
                    toolChain = originalToolChain
                }
            }
        """
    }

    def createTaskThatRunsInParallelUsingWorkerLeaseInjectionWith(String taskName) {
        setupParallelTaskAndExpectations(taskName)

        buildFile << """
            ${callbackWorkerLeaseService}

            tasks.matching { it.name == '${taskName}' }.all { task ->
                def workerLeaseService = task.asDynamicObject.publicType.getDeclaredField("workerLeaseService")
                workerLeaseService.setAccessible(true)
                workerLeaseService.set(task, new CallbackWorkerLeaseService(workerLeaseService.get(task), beforeOperations, afterOperations))
            }
        """
    }

    String getCallbackToolChain() {
        return """
            import java.lang.reflect.Field
            import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
            import org.gradle.nativeplatform.toolchain.internal.*
            import org.gradle.internal.operations.*
            import org.gradle.internal.progress.*

            ${callbackWorkerLeaseService}

            class CallbackToolChain implements NativeToolChainInternal {
                @Delegate
                final NativeToolChainInternal delegate

                final Closure beforeCallback
                final Closure afterCallback
                WorkerLeaseService originalWorkerLeaseService
                def decorated = []

                CallbackToolChain(NativeToolChainInternal delegate, Closure beforeCallback, Closure afterCallback) {
                    this.delegate = delegate
                    this.beforeCallback = beforeCallback
                    this.afterCallback = afterCallback
                }

                @Override
                PlatformToolProvider select(NativePlatformInternal targetPlatform) {
                    def toolProvider = delegate.select(targetPlatform)
                    if (! decorated.contains(toolProvider)) {
                        Field workerLeaseService = toolProvider.getClass().getDeclaredField("workerLeaseService")
                        workerLeaseService.setAccessible(true)
                        originalWorkerLeaseService = workerLeaseService.get(toolProvider)
                        workerLeaseService.set(toolProvider, new CallbackWorkerLeaseService(originalWorkerLeaseService, beforeCallback, afterCallback))
                        decorated << toolProvider
                    }
                    return toolProvider
                }

                void undecorateToolProviders() {
                    decorated.each { toolProvider ->
                        Field workerLeaseService = toolProvider.getClass().getDeclaredField("workerLeaseService")
                        workerLeaseService.setAccessible(true)
                        workerLeaseService.set(toolProvider, originalWorkerLeaseService)
                    }
                    decorated = []
                }
            }
        """
    }

    String getCallbackWorkerLeaseService() {
        return """
            import org.gradle.internal.work.WorkerLeaseService

            class CallbackWorkerLeaseService implements WorkerLeaseService {
                @Delegate
                final WorkerLeaseService delegate

                final Closure beforeCallback
                final Closure afterCallback

                CallbackWorkerLeaseService(WorkerLeaseService delegate, Closure beforeCallback, Closure afterCallback) {
                    this.delegate = delegate
                    this.beforeCallback = beforeCallback
                    this.afterCallback = afterCallback
                }

                public void runAsIsolatedTask(Runnable action) {
                    delegate.runAsIsolatedTask(new Runnable() {
                        public void run() {
                            beforeCallback.call()
                            try {
                                action.run();
                            } finally {
                                afterCallback.call()
                            }
                        }
                    })
                }
            }
        """
    }
}
