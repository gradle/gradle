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
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class NativeCompiler<T extends NativeCompileSpec> extends AbstractCompiler<T> {
    private final Transformer<T, T> specTransformer;
    private final String objectFileExtension;
    private final Logger logger = Logging.getLogger(NativeCompiler.class);

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;

    public NativeCompiler(BuildOperationExecutor buildOperationExecutor, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, ArgsTransformer<T> argsTransformer, Transformer<T, T> specTransformer, String objectFileExtension, boolean useCommandFile, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, argsTransformer, useCommandFile, workerLeaseService);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.objectFileExtension = objectFileExtension;
        this.specTransformer = specTransformer;
    }

    @Override
    public WorkResult execute(final T spec) {
        final T transformedSpec = specTransformer.transform(spec);

        super.execute(spec);

        return WorkResults.didWork(!transformedSpec.getSourceFiles().isEmpty());
    }

    // TODO(daniel): Should support in a better way multi file invocation.
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final T spec, final List<String> genericArgs) {
        final File objectDir = spec.getObjectFileDir();
        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                for (File sourceFile : spec.getSourceFiles()) {
                    CommandLineToolInvocation perFileInvocation = createPerFileInvocation(genericArgs, sourceFile, objectDir, spec);
                    buildQueue.add(perFileInvocation);
                }
            }
        };
    }

    protected List<String> getSourceArgs(File sourceFile) {
        return Collections.singletonList(sourceFile.getAbsolutePath());
    }

    protected abstract List<String> getOutputArgs(T spec, File outputFile);

    protected abstract void addOptionsFileArgs(List<String> args, File tempDir);

    protected abstract List<String> getPCHArgs(T spec);

    protected File getOutputFileDir(File sourceFile, File objectFileDir, String fileSuffix) {
        boolean windowsPathLimitation = OperatingSystem.current().isWindows();

        File outputFile = compilerOutputFileNamingSchemeFactory.create()
                .withObjectFileNameSuffix(fileSuffix)
                .withOutputBaseFolder(objectFileDir)
                .map(sourceFile);
        File outputDirectory = outputFile.getParentFile();
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        return windowsPathLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }

    protected List<String> maybeGetPCHArgs(final T spec, File sourceFile) {
        if (spec.getPreCompiledHeader() == null) {
            return Lists.newArrayList();
        }

        final IncludeDirectives includes = spec.getSourceFileIncludeDirectives().get(sourceFile);
        final String header = spec.getPreCompiledHeader();

        List<Include> headers = includes.getAll();
        boolean usePCH = !headers.isEmpty() && header.equals(headers.get(0).getValue());

        if (usePCH) {
            return getPCHArgs(spec);
        } else {
            boolean containsHeader = CollectionUtils.any(headers, new Spec<Include>() {
                @Override
                public boolean isSatisfiedBy(Include include) {
                    return include.getValue().equals(header);
                }
            });
            if (containsHeader) {
                logger.log(LogLevel.WARN, getCantUsePCHMessage(spec.getPreCompiledHeader(), sourceFile));
            }
            return Lists.newArrayList();
        }
    }

    private static String getCantUsePCHMessage(String pchHeader, File sourceFile) {
        return "The source file "
                .concat(sourceFile.getName())
                .concat(" includes the header ")
                .concat(pchHeader)
                .concat(" but it is not the first declared header, so the pre-compiled header will not be used.");
    }

    protected CommandLineToolInvocation createPerFileInvocation(List<String> genericArgs, File sourceFile, File objectDir, T spec) {
        List<String> sourceArgs = getSourceArgs(sourceFile);
        List<String> outputArgs = getOutputArgs(spec, getOutputFileDir(sourceFile, objectDir, objectFileExtension));
        List<String> pchArgs = maybeGetPCHArgs(spec, sourceFile);

        return newInvocation("compiling ".concat(sourceFile.getName()), objectDir, buildPerFileArgs(genericArgs, sourceArgs, outputArgs, pchArgs), spec.getOperationLogger());
    }

    protected Iterable<String> buildPerFileArgs(List<String> genericArgs, List<String> sourceArgs, List<String> outputArgs, List<String> pchArgs) {
        return Iterables.concat(genericArgs, pchArgs, sourceArgs, outputArgs);
    }
}
