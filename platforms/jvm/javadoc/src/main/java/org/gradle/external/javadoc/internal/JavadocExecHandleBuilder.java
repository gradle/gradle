/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import org.gradle.api.GradleException;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.io.IOException;

public class JavadocExecHandleBuilder {
    private final ExecActionFactory execActionFactory;
    private File execDirectory;
    private MinimalJavadocOptions options;
    private File optionsFile;
    private String executable;

    public JavadocExecHandleBuilder(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    public JavadocExecHandleBuilder execDirectory(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("execDirectory == null!");
        }
        if (!directory.exists()) {
            throw new IllegalArgumentException("execDirectory doesn't exists!");
        }
        if (directory.isFile()) {
            throw new IllegalArgumentException("execDirectory is a file");
        }

        this.execDirectory = directory;
        return this;
    }

    public JavadocExecHandleBuilder options(MinimalJavadocOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options == null!");
        }

        this.options = options;
        return this;
    }

    public JavadocExecHandleBuilder optionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
        return this;
    }

    public ExecAction getExecHandle() {
        try {
            options.write(optionsFile);
        } catch (IOException e) {
            throw new GradleException("Failed to store javadoc options.", e);
        }

        ExecAction execAction = execActionFactory.newExecAction();
        execAction.workingDir(execDirectory);
        execAction.executable(GUtil.getOrDefault(executable, () -> Jvm.current().getJavadocExecutable()));
        execAction.args("@" + optionsFile.getAbsolutePath());

        options.contributeCommandLineOptions(execAction);

        return execAction;
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }
}
