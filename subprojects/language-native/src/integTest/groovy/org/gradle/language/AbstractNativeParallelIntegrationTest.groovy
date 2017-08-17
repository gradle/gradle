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
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
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

    def withTaskThatRunsParallelWith(String taskName) {
        server.start()
        server.expectConcurrent("operationsStarted", "parallelTaskStarted")
        server.expectConcurrent("operationsFinished", "parallelTaskFinished")

        buildFile << """
            ${callbackToolChain}
            
            def beforeOperations = { ${server.callFromBuild("operationsStarted")} }
            def afterOperations = { ${server.callFromBuild("operationsFinished")} }

            tasks.matching { it.name == '${taskName}' }.all {
                doFirst {
                    setToolChain(new CallbackToolChain(toolChain, beforeOperations, afterOperations))
                }
                doLast {
                    toolChain.undecorateToolProviders()
                }
            }
            
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

    String getCallbackToolChain() {
        return """
            import java.lang.reflect.Field
            import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
            import org.gradle.nativeplatform.toolchain.internal.*
            import org.gradle.internal.operations.*
            import org.gradle.internal.progress.*
            
            class CallbackToolChain implements NativeToolChainInternal { 
                @Delegate
                final NativeToolChainInternal delegate
                
                final Closure beforeCallback
                final Closure afterCallback
                BuildOperationExecutor originalBuildExecutor
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
                        Field buildOperationExecutor = AbstractPlatformToolProvider.class.getDeclaredField("buildOperationExecutor")
                        buildOperationExecutor.setAccessible(true)
                        originalBuildExecutor = buildOperationExecutor.get(toolProvider)
                        buildOperationExecutor.set(toolProvider, new CallbackBuildOperationExecutor(originalBuildExecutor, beforeCallback, afterCallback))
                        decorated << toolProvider
                    }
                    return toolProvider   
                }
                
                void undecorateToolProviders() {
                    decorated.each { toolProvider ->
                        Field buildOperationExecutor = AbstractPlatformToolProvider.class.getDeclaredField("buildOperationExecutor")
                        buildOperationExecutor.setAccessible(true)
                        buildOperationExecutor.set(toolProvider, originalBuildExecutor)
                    }
                    decorated = []
                }
            }
        
            class CallbackBuildOperationExecutor implements BuildOperationExecutor {
                @Delegate
                final BuildOperationExecutor delegate
                
                final Closure beforeCallback
                final Closure afterCallback
        
                CallbackBuildOperationExecutor(BuildOperationExecutor delegate, Closure beforeCallback, Closure afterCallback) {
                    this.delegate = delegate
                    this.beforeCallback = beforeCallback
                    this.afterCallback = afterCallback
                }
        
                public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
                    beforeCallback.call()
                    try {
                        delegate.runAll(worker, schedulingAction);
                    } finally {
                        afterCallback.call()
                    }
                }        
            }
        """
    }
}
