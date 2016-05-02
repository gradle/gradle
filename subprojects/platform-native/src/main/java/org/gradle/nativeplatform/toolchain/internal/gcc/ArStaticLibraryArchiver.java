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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
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

/**
 * A static library archiver based on the GNU 'ar' utility
 */
class ArStaticLibraryArchiver implements Compiler<StaticLibraryArchiverSpec> {
    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final ArgsTransformer<StaticLibraryArchiverSpec> argsTransformer = new ArchiverSpecToArguments();
    private final CommandLineToolContext invocationContext;
    private final BuildOperationProcessor buildOperationProcessor;

    ArStaticLibraryArchiver(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext) {
        this.buildOperationProcessor = buildOperationProcessor;
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.invocationContext = invocationContext;
    }

    @Override
    public WorkResult execute(final StaticLibraryArchiverSpec spec) {
        deletePreviousOutput(spec);

        List<String> args = argsTransformer.transform(spec);
        invocationContext.getArgAction().execute(args);
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

    private void deletePreviousOutput(StaticLibraryArchiverSpec spec) {
        // Need to delete the previous archive, otherwise stale object files will remain
        if (!spec.getOutputFile().isFile()) {
            return;
        }
        if (!(spec.getOutputFile().delete())) {
            throw new GradleException("Create static archive failed: could not delete previous archive");
        }
    }

    private static class ArchiverSpecToArguments implements ArgsTransformer<StaticLibraryArchiverSpec> {
        @Override
        public List<String> transform(StaticLibraryArchiverSpec spec) {
            List<String> args = new ArrayList<String>();
            // -r : Add files to static archive, creating if required
            // -c : Don't write message to standard error when creating archive
            // -s : Create an object file index (equivalent to running 'ranlib')
            args.add("-rcs");
            args.addAll(spec.getAllArgs());
            args.add(spec.getOutputFile().getAbsolutePath());
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
