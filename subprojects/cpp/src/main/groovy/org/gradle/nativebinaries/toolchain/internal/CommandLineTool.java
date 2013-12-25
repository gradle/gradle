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
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;

public class CommandLineTool<T extends BinaryToolSpec> {
    private final String action;
    private final File executable;
    private final ExecActionFactory execActionFactory;
    private final Map<String, String> environment = new HashMap<String, String>();
    private final List<File> path = new ArrayList<File>();
    private ArgsTransformer<T> specToArgs;
    private Transformer<T, T> specTransformer = new IdentityTransformer<T>();
    private File workDir;

    public CommandLineTool(String action, File executable, ExecActionFactory execActionFactory) {
        this.action = action;
        this.executable = executable;
        this.execActionFactory = execActionFactory;
    }

    public CommandLineTool<T> inWorkDirectory(File workDir) {
        GFileUtils.mkdirs(workDir);
        this.workDir = workDir;
        return this;
    }

    public CommandLineTool<T> withSpecTransformer(Transformer<T, T> specAction) {
        this.specTransformer = specAction;
        return this;
    }

    public CommandLineTool<T> withPath(List<File> pathEntries) {
        path.addAll(pathEntries);
        return this;
    }

    public CommandLineTool<T> withPath(File... pathEntries) {
        Collections.addAll(path, pathEntries);
        return this;
    }

    public CommandLineTool<T> withEnvironmentVar(String name, String value) {
        environment.put(name, value);
        return this;
    }

    public CommandLineTool<T> withArguments(ArgsTransformer<T> arguments) {
        this.specToArgs = arguments;
        return this;
    }

    public WorkResult execute(T spec) {
        ExecAction compiler = execActionFactory.newExecAction();
        compiler.executable(executable);
        if (workDir != null) {
            compiler.workingDir(workDir);
        }

        List<String> args = specToArgs.transform(specTransformer.transform(spec));
        compiler.args(args);

        if (!path.isEmpty()) {
            String pathVar = OperatingSystem.current().getPathVar();
            String compilerPath = Joiner.on(File.pathSeparator).join(path);
            compilerPath = compilerPath + File.pathSeparator + System.getenv(pathVar);
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

    static class IdentityTransformer<T> implements Transformer<T, T> {
        public T transform(T original) {
            return original;
        }
    }
}
