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

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineTool;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolInvocation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class GccLinker implements Compiler<LinkerSpec> {

    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<LinkerSpec> argsTransformer;
    private final CommandLineToolInvocation baseInvocation;
    private final boolean useCommandFile;

    public GccLinker(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, boolean useCommandFile) {
        this.argsTransformer = new GccLinkerArgsTransformer();
        this.baseInvocation = baseInvocation;
        this.useCommandFile = useCommandFile;
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(LinkerSpec spec) {
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        if (useCommandFile) {
            invocation.addPostArgsAction(new GccOptionsFileArgTransformer(spec.getTempDir()));
        }
        invocation.setArgs(argsTransformer.transform(spec));
        commandLineTool.execute(invocation);
        return new SimpleWorkResult(true);
    }

    private static class GccLinkerArgsTransformer implements ArgsTransformer<LinkerSpec> {
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
