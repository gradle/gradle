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

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.toolchain.internal.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

class LibExeStaticLibraryArchiver implements Compiler<StaticLibraryArchiverSpec> {
    private final CommandLineTool commandLineTool;
    private final ArgsTransformer<StaticLibraryArchiverSpec> args;
    private final CommandLineToolInvocation baseInvocation;

    public LibExeStaticLibraryArchiver(CommandLineTool commandLineTool, CommandLineToolInvocation invocation) {
        args = new LibExeSpecToArguments();
        this.commandLineTool = commandLineTool;
        this.baseInvocation = invocation;
    }

    public WorkResult execute(StaticLibraryArchiverSpec spec) {
        MutableCommandLineToolInvocation invocation = baseInvocation.copy();
        invocation.addPostArgsAction(new VisualCppOptionsFileArgTransformer(spec.getTempDir()));
        invocation.setArgs(args.transform(spec));
        commandLineTool.execute(invocation);
        return new SimpleWorkResult(true);
    }

    private static class LibExeSpecToArguments implements ArgsTransformer<StaticLibraryArchiverSpec> {
        public List<String> transform(StaticLibraryArchiverSpec spec) {
            List<String> args = new ArrayList<String>();
            args.add("/OUT:" + spec.getOutputFile().getAbsolutePath());
            args.add("/NOLOGO");
            args.addAll(escapeUserArgs(spec.getAllArgs()));
            for (File file : spec.getObjectFiles()) {
                args.add(file.getAbsolutePath());
            }
            return args;
        }
    }
}
