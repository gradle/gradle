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

package org.gradle.nativecode.toolchain.internal.gpp;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.nativecode.toolchain.internal.CommandLineTool;
import org.gradle.nativecode.base.internal.StaticLibraryArchiverSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

/**
 * A static library linker based on the GNU 'ar' utility
 */

class ArStaticLibraryArchiver implements Compiler<StaticLibraryArchiverSpec> {
    private final CommandLineTool<StaticLibraryArchiverSpec> commandLineTool;

    public ArStaticLibraryArchiver(File executable, Factory<ExecAction> execActionFactory) {
        this.commandLineTool = new CommandLineTool<StaticLibraryArchiverSpec>("Create static library", executable, execActionFactory)
                .withArguments(new ArchiverSpecToArguments());
    }

    public WorkResult execute(StaticLibraryArchiverSpec spec) {
        return commandLineTool.execute(spec);
    }

    private static class ArchiverSpecToArguments implements CompileSpecToArguments<StaticLibraryArchiverSpec> {
        public void collectArguments(StaticLibraryArchiverSpec spec, ArgCollector collector) {
            collector.args("-rc", spec.getOutputFile().getAbsolutePath());
            collector.args(spec.getArgs());
            for (File file : spec.getSource()) {
                collector.args(file.getAbsolutePath());
            }
        }
    }
}
