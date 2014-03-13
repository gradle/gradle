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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.SharedLibraryLinkerSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class GccLinker implements Compiler<LinkerSpec> {

    private final CommandLineTool<LinkerSpec> commandLineTool;

    public GccLinker(CommandLineTool<LinkerSpec> commandLineTool, Action<List<String>> argsAction, boolean useCommandFile) {
        ArgsTransformer<LinkerSpec> argsTransformer = new GccLinkerArgsTransformer();
        argsTransformer = new PostTransformActionArgsTransformer<LinkerSpec>(argsTransformer, argsAction);
        if (useCommandFile) {
            argsTransformer = new GccOptionsFileArgTransformer<LinkerSpec>(argsTransformer);
        }
        this.commandLineTool = commandLineTool.withArguments(argsTransformer);
    }

    public WorkResult execute(LinkerSpec spec) {
        return commandLineTool.execute(spec);
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
            for (File pathEntry : spec.getLibraryPath()) {
                // TODO:DAZ It's not clear to me what the correct meaning of this should be for GCC
//                args.add("-L" + pathEntry.getAbsolutePath());
//                args.add("-Wl,-L" + pathEntry.getAbsolutePath());
//                args.add("-Wl,-rpath," + pathEntry.getAbsolutePath());
                throw new UnsupportedOperationException("Library Path not yet supported on GCC");
            }

            for (String userArg : spec.getArgs()) {
                args.add(userArg);
            }

            return args;
        }

        private void maybeSetInstallName(SharedLibraryLinkerSpec spec, List<String> args) {
            String installName = spec.getInstallName();
            if (installName == null || OperatingSystem.current().isWindows()) {
                return;
            }
            if (OperatingSystem.current().isMacOsX()) {
                args.add("-Wl,-install_name," + installName);
            } else {
                args.add("-Wl,-soname," + installName);
            }
        }
    }
}
