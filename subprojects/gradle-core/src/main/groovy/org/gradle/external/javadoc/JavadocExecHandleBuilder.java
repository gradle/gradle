/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc;

import org.apache.tools.ant.util.JavaEnvUtils;
import org.gradle.api.GradleException;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;

import java.io.File;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class JavadocExecHandleBuilder {
    private File execDirectory;
    private MinimalJavadocOptions options;
    private File optionsFile;
    private File destinationDirectory;

    public JavadocExecHandleBuilder() {
    }

    public JavadocExecHandleBuilder execDirectory(File directory) {
        if ( directory == null ) throw new IllegalArgumentException("execDirectory == null!");
        if ( !directory.exists() ) throw new IllegalArgumentException("execDirectory doesn't exists!");
        if ( directory.isFile() ) throw new IllegalArgumentException("execDirectory is a file");

        this.execDirectory = directory;
        return this;
    }

    public JavadocExecHandleBuilder options(MinimalJavadocOptions options) {
        if ( options == null ) throw new IllegalArgumentException("options == null!");

        this.options = options;
        return this;
    }

    public JavadocExecHandleBuilder optionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
        return this;
    }

    public JavadocExecHandleBuilder destinationDirectory(File destinationDirectory) {
        if ( destinationDirectory == null ) throw new IllegalArgumentException("destinationDirectory == null!");
        if ( !destinationDirectory.exists() ) throw new IllegalArgumentException("destinationDirectory doesn't exists!");
        if ( destinationDirectory.isFile() ) throw new IllegalArgumentException("destinationDirectory is a file");

        this.destinationDirectory = destinationDirectory;
        return this;
    }

    public ExecHandle getExecHandle() {
        try {
            options.write(optionsFile);
        }
        catch ( IOException e ) {
            throw new GradleException("Faild to store javadoc options.", e);
        }

        final ExecHandleBuilder execHandleBuilder = new ExecHandleBuilder(true)
            .execDirectory(execDirectory)
            .execCommand(JavaEnvUtils.getJdkExecutable("javadoc")) // reusing Ant knowledge here would be stupid not to
            .arguments("@"+ optionsFile.getAbsolutePath());

        options.contributeCommandLineOptions(execHandleBuilder);

        return execHandleBuilder.getExecHandle();
    }
}
