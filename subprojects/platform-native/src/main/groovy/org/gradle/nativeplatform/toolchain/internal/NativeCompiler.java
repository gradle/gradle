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

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;

import java.io.File;
import java.util.Collections;
import java.util.List;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final ArgsTransformerFactory<T> argsTransformerFactory;
    private final Transformer<T, T> specTransformer;
    private final CommandLineToolContext invocationContext;
    private final ObjectFileExtensionCalculator objectFileExtensionCalculator;
    private final boolean useCommandFile;

    private final BuildOperationProcessor buildOperationProcessor;

    public NativeCompiler(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, ArgsTransformerFactory<T> argsTransformerFactory, Transformer<T, T> specTransformer, ObjectFileExtensionCalculator objectFileExtensionCalculator, boolean useCommandFile) {
        this.invocationContext = invocationContext;
        this.objectFileExtensionCalculator = objectFileExtensionCalculator;
        this.useCommandFile = useCommandFile;
        this.argsTransformerFactory = argsTransformerFactory;
        this.specTransformer = specTransformer;
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public WorkResult execute(T spec) {
        final T transformedSpec = specTransformer.transform(spec);
        final List<String> genericArgs = getArguments(transformedSpec);
        final BuildOperationQueue<CommandLineToolInvocation> buildQueue = buildOperationProcessor.newQueue(commandLineToolInvocationWorker);

        File objectDir = transformedSpec.getObjectFileDir();
        for (File sourceFile : transformedSpec.getSourceFiles()) {
            CommandLineToolInvocation perFileInvocation =
                    createPerFileInvocation(genericArgs, sourceFile, objectDir, spec);
            buildQueue.add(perFileInvocation);
        }

        // Wait on all executions to complete or fail
        buildQueue.waitForCompletion();

        return new SimpleWorkResult(!transformedSpec.getSourceFiles().isEmpty());
    }

    protected List<String> getArguments(T spec) {
        ArgsTransformer<T> argsTransformer = argsTransformerFactory.create(spec);
        List<String> args = argsTransformer.transform(spec);

        Action<List<String>> userArgTransformer = invocationContext.getArgAction();
        // modifies in place
        userArgTransformer.execute(args);

        if (useCommandFile) {
            // Shorten args and write out an options.txt file
            // This must be called only once per execute()
            addOptionsFileArgs(args, spec.getTempDir());
        }
        return args;
    }

    protected List<String> getSourceArgs(File sourceFile) {
        return Collections.singletonList(sourceFile.getAbsolutePath());
    }

    protected abstract List<String> getOutputArgs(T spec, File outputFile);

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

    protected CommandLineToolInvocation createPerFileInvocation(List<String> genericArgs, File sourceFile, File objectDir, T spec) {
        String objectFileSuffix = objectFileExtensionCalculator.transform(spec);
        List<String> sourceArgs = getSourceArgs(sourceFile);
        List<String> outputArgs = getOutputArgs(spec, getOutputFileDir(sourceFile, objectDir, objectFileSuffix));

        return invocationContext.createInvocation(String.format("compiling %s", sourceFile.getName()), objectDir, buildPerFileArgs(genericArgs, sourceArgs, outputArgs));
    }

    protected Iterable<String> buildPerFileArgs(List<String> genericArgs, List<String> sourceArgs, List<String> outputArgs) {
        return Iterables.concat(genericArgs, sourceArgs, outputArgs);
    }
}
