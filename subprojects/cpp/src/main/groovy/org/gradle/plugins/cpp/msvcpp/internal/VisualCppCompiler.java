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

package org.gradle.plugins.cpp.msvcpp.internal;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerArgumentsToOptionFile;
import org.gradle.plugins.cpp.compiler.internal.CommandLineTool;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

class VisualCppCompiler implements org.gradle.api.internal.tasks.compile.Compiler<CppCompileSpec> {

    private final CommandLineTool<CppCompileSpec> commandLineTool;

    VisualCppCompiler(File executable, Factory<ExecAction> execActionFactory) {
        this.commandLineTool = new CommandLineTool<CppCompileSpec>(executable, execActionFactory)
                .withArguments(new CommandLineCppCompilerArgumentsToOptionFile<CppCompileSpec>(
                ArgWriter.windowsStyleFactory(), new VisualCppCompileSpecToArguments()
        ));
    }

    public WorkResult execute(CppCompileSpec spec) {
        return commandLineTool.inWorkDirectory(spec.getObjectFileDir()).execute(spec);
    }

    private static class VisualCppCompileSpecToArguments implements CompileSpecToArguments<CppCompileSpec> {
        public void collectArguments(CppCompileSpec spec, ArgCollector collector) {
            collector.args(spec.getArgs());
            collector.args("/c");
            collector.args("/nologo");
            collector.args("/EHsc");
            if (spec.isForDynamicLinking()) {
                collector.args("/LD"); // TODO:DAZ Not sure if this has any effect at compile time
            }
            for (File file : spec.getIncludeRoots()) {
                collector.args("/I", file.getAbsolutePath());
            }
            for (File file : spec.getSource()) {
                collector.args(file);
            }
        }
    }
}
