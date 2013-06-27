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

package org.gradle.nativecode.toolchain.internal;

import org.gradle.api.internal.tasks.compile.CompileSpec;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.ExecSpecBackedArgCollector;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.process.internal.ExecAction;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CommandLineTool<T extends CompileSpec> {
    private final File executable;
    private final Factory<ExecAction> execActionFactory;
    private CompileSpecToArguments<T> toArguments;
    private File workDir;

    public CommandLineTool(File executable, Factory<ExecAction> execActionFactory) {
        this.executable = executable;
        this.execActionFactory = execActionFactory;
    }

    public CommandLineTool<T> inWorkDirectory(File workDir) {
        GFileUtils.mkdirs(workDir);
        this.workDir = workDir;
        return this;
    }

    public CommandLineTool<T> withArguments(CompileSpecToArguments<T> arguments) {
        this.toArguments = arguments;
        return this;
    }

    public WorkResult execute(T spec) {
        ExecAction compiler = execActionFactory.create();
        compiler.executable(executable);
        if (workDir != null) {
            compiler.workingDir(workDir);
        }

        toArguments.collectArguments(spec, new ExecSpecBackedArgCollector(compiler));

        compiler.execute();
        return new SimpleWorkResult(true);
    }
}
