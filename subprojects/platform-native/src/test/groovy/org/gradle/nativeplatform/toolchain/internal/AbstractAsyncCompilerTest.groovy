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

package org.gradle.nativeplatform.toolchain.internal

import com.google.common.util.concurrent.ListenableFuture
import org.gradle.api.Action
import org.gradle.api.internal.tasks.AsyncWorkResult
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class AbstractAsyncCompilerTest extends ConcurrentSpec {
    NativeExecutor nativeExecutor = Mock()
    AbstractAsyncCompiler asyncCompiler = new TestAsyncCompiler(Mock(BuildOperationExecutor), Mock(CommandLineToolInvocationWorker), Mock(CommandLineToolContext), Mock(ArgsTransformer), true, nativeExecutor)
    ListenableFuture future = Mock()

    def "submits compilation to native executor"() {
        when:
        asyncCompiler.execute(Mock(CCompileSpec))

        then:
        1 * nativeExecutor.submit(_)
    }

    def "work result delegates to the returned future"() {
        when:
        AsyncWorkResult workResult = asyncCompiler.execute(Mock(CCompileSpec))
        workResult.didWork

        then:
        1 * nativeExecutor.submit(_) >> future
        1 * future.get()

        when:
        workResult.waitForCompletion()

        then:
        1 * future.get()
    }

    def "waitForCompletion blocks until callbacks are complete"() {
        def listener

        when:
        AsyncWorkResult workResult = asyncCompiler.execute(Mock(CCompileSpec))

        then:
        1 * nativeExecutor.submit(_) >> future

        when:
        def callback = { instant.callback }
        workResult.onCompletion callback

        then:
        1 * future.addListener(_, _) >> { args -> listener = args[0] }

        when:
        async {
            start {
                workResult.waitForCompletion()
                instant.complete
            }
            start {
                thread.block()
                listener.run()
            }
        }

        then:
        instant.complete > instant.callback
    }

    def "callbacks can complete before waitForCompletion is called"() {
        def listener

        when:
        AsyncWorkResult workResult = asyncCompiler.execute(Mock(CCompileSpec))

        then:
        1 * nativeExecutor.submit(_) >> future

        when:
        def callback = { instant.callback }
        workResult.onCompletion callback

        then:
        1 * future.addListener(_, _) >> { args -> listener = args[0] }

        when:
        async {
            listener.run()
            start {
                workResult.waitForCompletion()
                instant.complete
            }
        }

        then:
        instant.complete > instant.callback
    }

    private static class TestAsyncCompiler extends AbstractAsyncCompiler<CCompileSpec> {
        TestAsyncCompiler(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, ArgsTransformer<CCompileSpec> argsTransformer, boolean useCommandFile, NativeExecutor nativeExecutor) {
            super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, argsTransformer, useCommandFile, nativeExecutor)
        }

        @Override
        protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(CCompileSpec spec) {
            return null
        }

        @Override
        protected void addOptionsFileArgs(List<String> args, File tempDir) { }
    }
}
