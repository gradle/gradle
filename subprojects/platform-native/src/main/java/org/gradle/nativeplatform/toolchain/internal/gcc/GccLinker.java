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
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class GccLinker extends AbstractCompiler<LinkerSpec> {
    GccLinker(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, boolean useCommandFile, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new GccLinkerArgsTransformer(), useCommandFile, workerLeaseService);
    }

    @Override
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final LinkerSpec spec) {
        List<String> args = getArguments(spec);

        final CommandLineToolInvocation invocation = newInvocation(
            "linking " + spec.getOutputFile().getName(), spec.getOutputFile().getParentFile(), args, spec.getOperationLogger());

        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                buildQueue.add(invocation);
            }
        };
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
        new GccOptionsFileArgsWriter(tempDir).execute(args);
    }

    private static class GccLinkerArgsTransformer implements ArgsTransformer<LinkerSpec> {
        @Override
        public List<String> transform(LinkerSpec spec) {
            List<String> args = new ArrayList<String>();

            args.addAll(spec.getSystemArgs());

            if (spec instanceof SharedLibraryLinkerSpec) {
                args.add("-shared");
                maybeSetInstallName((SharedLibraryLinkerSpec) spec, args);
            }
            args.add("-o");
            args.add(spec.getOutputFile().getAbsolutePath());
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            for (File file : spec.getLibraries()) {
                args.add(file.getAbsolutePath());
            }
            if (!spec.getLibraryPath().isEmpty()) {
                throw new UnsupportedOperationException("Library Path not yet supported on GCC");
            }

            for (String userArg : spec.getArgs()) {
                args.add(userArg);
            }

            return args;
        }

        private void maybeSetInstallName(SharedLibraryLinkerSpec spec, List<String> args) {
            String installName = spec.getInstallName();
            OperatingSystem targetOs = spec.getTargetPlatform().getOperatingSystem();

            if (installName == null || targetOs.isWindows()) {
                return;
            }
            if (targetOs.isMacOsX()) {
                args.add("-Wl,-install_name," + installName);
            } else {
                args.add("-Wl,-soname," + installName);
            }
        }
    }
}
