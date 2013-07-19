/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativecode.toolchain.internal.gpp;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.language.cpp.internal.CppCompileSpec;
import org.gradle.nativecode.toolchain.internal.CommandLineTool;
import org.gradle.process.internal.ExecAction;

import java.io.File;

class CppCompiler implements Compiler<CppCompileSpec> {

    private final CommandLineTool<CppCompileSpec> commandLineTool;

    public CppCompiler(File executable, Factory<ExecAction> execActionFactory, boolean useCommandFile) {
        GccCompileSpecToArguments<CppCompileSpec> specToArguments = new GccCompileSpecToArguments<CppCompileSpec>(
                new CppCompileOptionsToArguments(),
                new GccCompileSourcesToArguments<CppCompileSpec>(),
                useCommandFile
        );
        this.commandLineTool = new CommandLineTool<CppCompileSpec>("C++ compile", executable, execActionFactory).withArguments(specToArguments);
    }

    public WorkResult execute(CppCompileSpec spec) {
        return commandLineTool.inWorkDirectory(spec.getObjectFileDir()).execute(spec);
    }

    // Certain options do not function correctly via an option file, so only use option file for headers and sources
    private static class CppCompileOptionsToArguments implements CompileSpecToArguments<CppCompileSpec> {
        public void collectArguments(CppCompileSpec spec, ArgCollector collector) {
            // C++-compiling options
            collector.args("-x", "c++");

            // TODO:DAZ Extract common stuff out
            // General G++ compiler options
            for (String macro : spec.getMacros()) {
                collector.args("-D" + macro);
            }
            collector.args(spec.getArgs());
            collector.args("-c");
            if (spec.isPositionIndependentCode()) {
                if (!OperatingSystem.current().isWindows()) {
                    collector.args("-fPIC");
                }
            }
        }
    }
}
