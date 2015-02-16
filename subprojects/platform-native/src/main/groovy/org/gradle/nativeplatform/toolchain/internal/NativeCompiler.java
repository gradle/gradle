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

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.OperationQueue;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;

import java.io.File;
import java.util.List;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final ArgsTransformer<T> argsTransformer;
    private final Transformer<T, T> specTransformer;
    private final CommandLineToolInvocation baseInvocation;
    private final String objectFileSuffix;
    private final boolean useCommandFile;

    private final BuildOperationProcessor buildOperationProcessor;

    public NativeCompiler(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolInvocation baseInvocation, ArgsTransformer<T> argsTransformer, Transformer<T, T> specTransformer, String objectFileSuffix, boolean useCommandFile) {
        this.baseInvocation = baseInvocation;
        this.objectFileSuffix = objectFileSuffix;
        this.useCommandFile = useCommandFile;
        this.argsTransformer = argsTransformer;
        this.specTransformer = specTransformer;
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public WorkResult execute(T spec) {
        final T transformedSpec = specTransformer.transform(spec);
        final List<String> genericArgs = getArguments(transformedSpec);
        final OperationQueue<CommandLineToolInvocation> buildQueue = buildOperationProcessor.newQueue(commandLineToolInvocationWorker);

        File objectDir = transformedSpec.getObjectFileDir();
        for (File sourceFile : transformedSpec.getSourceFiles()) {
            CommandLineToolInvocation perFileInvocation =
                    createPerFileInvocation(genericArgs, sourceFile, objectDir);
            buildQueue.add(perFileInvocation);
        }

        // Wait on all executions to complete or fail
        buildQueue.waitForCompletion();

        return new SimpleWorkResult(!transformedSpec.getSourceFiles().isEmpty());
    }

    protected List<String> getArguments(T spec) {
        // TODO: Detangle post args actions from invocation?
        MutableCommandLineToolInvocation postArgsInvocation = baseInvocation.copy();
        postArgsInvocation.setArgs(argsTransformer.transform(spec));
        // NOTE: this triggers the "post args" actions that can modify the arguments
        List<String> genericArgs = postArgsInvocation.getArgs();

        if (useCommandFile) {
            // Shorten args and write out an options.txt file
            // This must be called only once per execute()
            addOptionsFileArgs(genericArgs, spec.getTempDir());
        }
        return genericArgs;
    }

    protected void addSourceArgs(List<String> args, File sourceFile) {
        args.add(sourceFile.getAbsolutePath());
    }

    protected abstract void addOutputArgs(List<String> args, File outputFile);

    protected abstract void addOptionsFileArgs(List<String> args, File tempDir);

    protected File getOutputFileDir(File sourceFile, File objectFileDir, String fileSuffix) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        File outputFile = new CompilerOutputFileNamingScheme()
                .withObjectFileNameSuffix(fileSuffix)
                .withOutputBaseFolder(objectFileDir)
                .map(sourceFile);
        File outputDirectory = outputFile.getParentFile();
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        return windowsPathLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }

    protected CommandLineToolInvocation createPerFileInvocation(List<String> genericArgs, File sourceFile, File objectDir) {
        List<String> perFileArgs = Lists.newArrayList(genericArgs);
        addSourceArgs(perFileArgs, sourceFile);
        addOutputArgs(perFileArgs, getOutputFileDir(sourceFile, objectDir, objectFileSuffix));

        MutableCommandLineToolInvocation perFileInvocation = baseInvocation.copy();
        perFileInvocation.clearPostArgsActions();
        perFileInvocation.setWorkDirectory(objectDir);
        perFileInvocation.setArgs(perFileArgs);
        return perFileInvocation;
    }
}
