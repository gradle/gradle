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
import java.util.concurrent.TimeUnit;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private static final String NATIVE_PARALLEL_TOGGLE = "org.gradle.parallel.native";

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<T> argsTransformer;
    private final Transformer<T, T> specTransformer;
    private final CommandLineToolInvocation baseInvocation;
    private final String objectFileSuffix;
    private final boolean useCommandFile;

    public NativeCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, ArgsTransformer<T> argsTransformer, Transformer<T, T> specTransformer, String objectFileSuffix, boolean useCommandFile) {
        this.baseInvocation = baseInvocation;
        this.objectFileSuffix = objectFileSuffix;
        this.useCommandFile = useCommandFile;
        this.argsTransformer = argsTransformer;
        this.specTransformer = specTransformer;
        this.commandLineTool = commandLineTool;
    }

    private boolean useParallelCompile() {
        // TODO: This needs to be fed from command line settings
        return Boolean.getBoolean(NATIVE_PARALLEL_TOGGLE);
    }

    public WorkResult execute(T spec) {
        final T transformedSpec = specTransformer.transform(spec);
        CompileSpecToArgsTransformerChain<T> chain = new CompileSpecToArgsTransformerChain<T>(argsTransformer);

        if (useCommandFile) {
            chain.withTransformation(optionsFileTransformer(transformedSpec));
        }
        ArgsTransformer<T> genericArgTransformer = new ShortCircuitArgsTransformer<T>(chain);

        // cache results of transforming into initial set of arguments
        // NOTE: this triggers the "post args" actions that can modify the arguments
        // and write out an options.txt file
        genericArgTransformer.transform(transformedSpec);

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
            CompileSpecToArgsTransformerChain<T> perFileChain = new CompileSpecToArgsTransformerChain<T>(genericArgTransformer);
            perFileChain.withTransformation(new SingleSourceCompileArgTransformer(sourceFile));
            perFileChain.withTransformation(outputFileTransformer(sourceFile, spec.getObjectFileDir(), objectFileSuffix, windowsPathLimitation));

            MutableCommandLineToolInvocation perFileInvocation = baseInvocation.copy();
            perFileInvocation.setWorkDirectory(transformedSpec.getObjectFileDir());
            perFileInvocation.setArgs(perFileChain.transform(transformedSpec));
            // triggers post args actions again
            executor.execute(commandLineTool.toRunnableExecution(perFileInvocation));
        }

        executor.stop();

        return new SimpleWorkResult(!transformedSpec.getSourceFiles().isEmpty());
    }

    protected abstract OutputFileArgTransformer outputFileTransformer(File sourceFile, File objectFileDir, String objectFileNameSuffix, boolean windowsPathLengthLimitation);

    protected abstract OptionsFileArgsWriter optionsFileTransformer(T spec);

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
