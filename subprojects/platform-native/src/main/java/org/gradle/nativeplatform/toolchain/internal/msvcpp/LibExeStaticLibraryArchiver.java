/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class LibExeStaticLibraryArchiver implements Compiler<StaticLibraryArchiverSpec> {
    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final Transformer<StaticLibraryArchiverSpec, StaticLibraryArchiverSpec> specTransformer;

    private final ArgsTransformer<StaticLibraryArchiverSpec> argsTransformer;
    private final CommandLineToolContext invocationContext;
    private final BuildOperationProcessor buildOperationProcessor;

    LibExeStaticLibraryArchiver(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, Transformer<StaticLibraryArchiverSpec, StaticLibraryArchiverSpec> specTransformer) {
        this.buildOperationProcessor = buildOperationProcessor;
        this.specTransformer = specTransformer;
        this.argsTransformer = new LibExeSpecToArguments();
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.invocationContext = invocationContext;
    }

    @Override
    public WorkResult execute(final StaticLibraryArchiverSpec spec) {
        final StaticLibraryArchiverSpec transformedSpec = specTransformer.transform(spec);
        final List<String> args = argsTransformer.transform(transformedSpec);
        invocationContext.getArgAction().execute(args);
        new VisualCppOptionsFileArgsWriter(spec.getTempDir()).execute(args);
        final CommandLineToolInvocation invocation = invocationContext.createInvocation(
                "archiving " + spec.getOutputFile().getName(), args, spec.getOperationLogger());

        buildOperationProcessor.run(commandLineToolInvocationWorker, new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                buildQueue.add(invocation);
            }
        });

        return new SimpleWorkResult(true);
    }

    private static class LibExeSpecToArguments implements ArgsTransformer<StaticLibraryArchiverSpec> {
        @Override
        public List<String> transform(StaticLibraryArchiverSpec spec) {
            List<String> args = new ArrayList<String>();
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath());
            args.add("/NOLOGO");
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
