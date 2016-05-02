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
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec;
import org.gradle.nativeplatform.toolchain.internal.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class LinkExeLinker implements Compiler<LinkerSpec> {

    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final Transformer<LinkerSpec, LinkerSpec> specTransformer;
    private final ArgsTransformer<LinkerSpec> argsTransformer;
    private final CommandLineToolContext invocationContext;
    private final BuildOperationProcessor buildOperationProcessor;

    LinkExeLinker(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, Transformer<LinkerSpec, LinkerSpec> specTransformer) {
        this.buildOperationProcessor = buildOperationProcessor;
        this.argsTransformer = new LinkerArgsTransformer();
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.invocationContext = invocationContext;
        this.specTransformer = specTransformer;
    }

    @Override
    public WorkResult execute(final LinkerSpec spec) {
        LinkerSpec transformedSpec = specTransformer.transform(spec);
        List<String> args = argsTransformer.transform(transformedSpec);
        invocationContext.getArgAction().execute(args);
        new VisualCppOptionsFileArgsWriter(spec.getTempDir()).execute(args);
        final CommandLineToolInvocation invocation = invocationContext.createInvocation(
                "linking " + spec.getOutputFile().getName(), args, spec.getOperationLogger());

        buildOperationProcessor.run(commandLineToolInvocationWorker, new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                buildQueue.add(invocation);
            }
        });

        return new SimpleWorkResult(true);
    }

    private static class LinkerArgsTransformer implements ArgsTransformer<LinkerSpec> {
        @Override
        public List<String> transform(LinkerSpec spec) {
            List<String> args = new ArrayList<String>();
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath());
            args.add("/NOLOGO");
            if (spec instanceof SharedLibraryLinkerSpec) {
                args.add("/DLL");
            }
            for (File pathEntry : spec.getLibraryPath()) {
                args.add("/LIBPATH:" + pathEntry.getAbsolutePath());
            }
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            for (File file : spec.getLibraries()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
