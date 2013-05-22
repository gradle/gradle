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

package org.gradle.plugins.cpp.compiler.internal;

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.ExecSpecBackedArgCollector;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.plugins.binaries.model.BinaryCompileSpec;
import org.gradle.process.internal.ExecAction;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CommandLineCppCompiler<T extends BinaryCompileSpec> implements Compiler<T> {
    private final File executable;
    private final Factory<ExecAction> execActionFactory;
    private final CompileSpecToArguments<T> toArguments;

    public CommandLineCppCompiler(File executable, Factory<ExecAction> execActionFactory, CompileSpecToArguments<T> toArguments) {
        this.executable = executable;
        this.execActionFactory = execActionFactory;
        this.toArguments = toArguments;
    }

    public WorkResult execute(T spec) {
        File workDir = spec.getWorkDir();

        ensureDirsExist(workDir, spec.getOutputFile().getParentFile());

        ExecAction compiler = execActionFactory.create();
        compiler.executable(executable);
        compiler.workingDir(workDir);

        toArguments.collectArguments(spec, new ExecSpecBackedArgCollector(compiler));

        compiler.execute();
        return new SimpleWorkResult(true);
    }

    private void ensureDirsExist(File... dirs) {
        for (File dir : dirs) {
            GFileUtils.mkdirs(dir);
        }
    }

}
