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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private static final String NATIVE_PARALLEL_TOGGLE = "org.gradle.parallel.native";

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<T> argsTransformer;
    private final Transformer<T, T> specTransformer;
    private final CommandLineToolInvocation baseInvocation;
    private final String objectFileSuffix;
    private final boolean useCommandFile;
    private final OutputFileArgTransformer outputFileArgTransformer;

    public NativeCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, ArgsTransformer<T> argsTransformer, Transformer<T, T> specTransformer, OutputFileArgTransformer outputFileArgTransformer, String objectFileSuffix, boolean useCommandFile) {
        this.baseInvocation = baseInvocation;
        this.objectFileSuffix = objectFileSuffix;
        this.useCommandFile = useCommandFile;
        this.argsTransformer = argsTransformer;
        this.specTransformer = specTransformer;
        this.commandLineTool = commandLineTool;
        this.outputFileArgTransformer = outputFileArgTransformer;
    }

    private boolean useParallelCompile() {
        // TODO: This needs to be fed from command line settings
        return Boolean.getBoolean(NATIVE_PARALLEL_TOGGLE);
    }

    public WorkResult execute(T spec) {

        final MutableCommandLineToolInvocation invocation = baseInvocation.copy();

        T transformedSpec = specTransformer.transform(spec);
        invocation.setArgs(argsTransformer.transform(transformedSpec));

        // TODO: SLG this seems like an ugly way of doing this
        // triggers post args actions
        List<String> invocationArgs = invocation.getArgs();

        if (useCommandFile) {
            // force write of options.txt and mutation of invocationArgs
            getOptionsWriter(transformedSpec).execute(invocationArgs);
        }

        final StoppableExecutor executor;
        if (useParallelCompile()) {
            // TODO: This needs to limit # of threads
            executor = new DefaultExecutorFactory().create(commandLineTool.getDisplayName());
        } else {
            // Single threaded build
            executor = new CallingThreadExecutor();
        }
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        for (File sourceFile : transformedSpec.getSourceFiles()) {
            SingleSourceCompileArgTransformer<T> sourceArgTransformer = new SingleSourceCompileArgTransformer<T>(sourceFile,
                    objectFileSuffix,
                    invocationArgs,
                    windowsPathLimitation,
                    outputFileArgTransformer);

            MutableCommandLineToolInvocation perFileInvocation = invocation.copy();
            perFileInvocation.setWorkDirectory(transformedSpec.getObjectFileDir());
            perFileInvocation.setArgs(sourceArgTransformer.transform(transformedSpec));
            // triggers post args actions again
            executor.execute(commandLineTool.toRunnableExecution(perFileInvocation));
        }

        executor.stop();

        return new SimpleWorkResult(!transformedSpec.getSourceFiles().isEmpty());
    }

    protected abstract OptionsFileArgsWriter getOptionsWriter(T spec);

    /**
     * Re-uses calling thread for execute() call
     */
    private static class CallingThreadExecutor implements StoppableExecutor {
        @Override
        public void stop() { }

        @Override
        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException { }

        @Override
        public void requestStop() { }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
