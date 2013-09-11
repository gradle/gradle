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

package org.gradle.nativebinaries.toolchain.internal;

import com.google.common.base.Joiner;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompileSpec;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.api.internal.tasks.compile.ExecSpecBackedArgCollector;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecException;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandLineTool<T extends CompileSpec> {
    private final String action;
    private final File executable;
    private final Factory<ExecAction> execActionFactory;
    private final Map<String, String> environment = new HashMap<String, String>();
    private List<String> arguments = new ArrayList<String>();
    private CompileSpecToArguments<T> toArguments;
    private File workDir;
    private String path;

    public CommandLineTool(String action, File executable, Factory<ExecAction> execActionFactory) {
        this.action = action;
        this.executable = executable;
        this.execActionFactory = execActionFactory;
    }

    public CommandLineTool<T> inWorkDirectory(File workDir) {
        GFileUtils.mkdirs(workDir);
        this.workDir = workDir;
        return this;
    }

    public CommandLineTool<T> withPath(List<File> pathEntries) {
        path = Joiner.on(File.pathSeparator).join(pathEntries);
        return this;
    }

    public CommandLineTool<T> withEnvironment(Map<String, String> environment) {
        this.environment.putAll(environment);
        return this;
    }

    public CommandLineTool<T> withArguments(CompileSpecToArguments<T> arguments) {
        this.toArguments = arguments;
        return this;
    }

    public CommandLineTool<T> withArguments(List<String> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    public WorkResult execute(T spec) {
        ExecAction compiler = execActionFactory.create();
        compiler.executable(executable);
        if (workDir != null) {
            compiler.workingDir(workDir);
        }

        if (!arguments.isEmpty()) {
            compiler.args(arguments);
        }
        toArguments.collectArguments(spec, new ExecSpecBackedArgCollector(compiler));

        if (GUtil.isTrue(path)) {
            String pathVar = OperatingSystem.current().getPathVar();
            String compilerPath = path + File.pathSeparator + System.getenv(pathVar);
            compiler.environment(pathVar, compilerPath);
        }

        compiler.environment(environment);

        try {
            compiler.execute();
        } catch (ExecException e) {
            throw new GradleException(String.format("%s failed; see the error output for details.", action), e);
        }
        return new SimpleWorkResult(true);
    }
}
