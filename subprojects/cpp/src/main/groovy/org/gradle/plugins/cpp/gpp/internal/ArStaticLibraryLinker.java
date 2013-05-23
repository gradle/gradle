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

package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.internal.Factory;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompiler;
import org.gradle.plugins.cpp.internal.LinkerSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

/**
 * A static library linker based on the GNU 'ar' utility
 */
public class ArStaticLibraryLinker extends CommandLineCppCompiler<LinkerSpec> {

    public ArStaticLibraryLinker(File executable, Factory<ExecAction> execActionFactory) {
        super(executable, execActionFactory, new LinkerSpecToArguments());
    }

    private static class LinkerSpecToArguments implements CompileSpecToArguments<LinkerSpec> {
        public void collectArguments(LinkerSpec spec, ArgCollector collector) {
            collector.args("-rc", spec.getOutputFile().getAbsolutePath());
            for (File file : spec.getObjectFiles()) {
                collector.args(file.getAbsolutePath());
            }
            collector.args(spec.getArgs());
        }
    }
}
