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

import groovy.lang.Closure;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.ExecAction;

import java.io.File;

public abstract class CommandLineCppCompiler implements Compiler<GppCompileSpec> {
    private final FileResolver fileResolver;

    public CommandLineCppCompiler(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public WorkResult execute(GppCompileSpec spec) {
        File workDir = spec.getWorkDir();

        ensureDirsExist(workDir, spec.getOutputFile().getParentFile());

        ExecAction compiler = new DefaultExecAction(fileResolver);
        compiler.executable(OperatingSystem.current().findInPath(getExecutable()));
        compiler.workingDir(workDir);

        configure(compiler, spec);
        // Apply all of the settings
        for (Closure closure : spec.getSettings()) {
            closure.call(compiler);
        }

        compiler.execute();
        return new SimpleWorkResult(true);
    }

    protected abstract void configure(ExecAction compiler, GppCompileSpec spec);

    protected abstract String getExecutable();

    private void ensureDirsExist(File... dirs) {
        for (File dir : dirs) {
            dir.mkdirs();
        }
    }

}
