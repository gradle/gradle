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

package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompiler;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerArgumentsToOptionFile;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

public class GppCompiler extends CommandLineCppCompiler<CppCompileSpec> {

    public GppCompiler(File executable, Factory<ExecAction> execActionFactory, boolean useCommandFile) {
        super(executable, execActionFactory, useCommandFile ? viaCommandFile() : withoutCommandFile());
    }

    private static GppCompileSpecToArguments withoutCommandFile() {
        return new GppCompileSpecToArguments();
    }

    private static CommandLineCppCompilerArgumentsToOptionFile<CppCompileSpec> viaCommandFile() {
        return new CommandLineCppCompilerArgumentsToOptionFile<CppCompileSpec>(
            ArgWriter.unixStyleFactory(), new GppCompileSpecToArguments()
        );
    }

    private static class GppCompileSpecToArguments implements CompileSpecToArguments<CppCompileSpec> {
        public void collectArguments(CppCompileSpec spec, ArgCollector collector) {
            collector.args(spec.getArgs());
            collector.args("-c");
            if (spec.isForDynamicLinking()) {
                if (!OperatingSystem.current().isWindows()) {
                    collector.args("-fPIC");
                }
            }
            for (File file : spec.getIncludeRoots()) {
                collector.args("-I");
                collector.args(file.getAbsolutePath());
            }
            for (File file : spec.getSource()) {
                collector.args(file.getAbsolutePath());
            }
        }
    }
}
