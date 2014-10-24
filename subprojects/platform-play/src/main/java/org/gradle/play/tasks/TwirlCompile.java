/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.play.internal.twirl.DaemonTwirlCompiler;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompiler;

import java.io.File;

/**
 * Task for compiling twirl templates
 */
public class TwirlCompile extends SourceTask {

    /**
     * FileCollection presenting the twirl compiler classpath.
     */
    private FileCollection compilerClasspath;


    /**
     * Target directory for the compiled template files.
     */
    private File outputDirectory;

    @InputFiles
    public FileCollection getCompilerClasspath() {
        return compilerClasspath;
    }

    public void setCompilerClasspath(FileCollection compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
    }

    /**
     * Returns the directory to generate the parser source files into.
     *
     * @return The output directory.
     */
    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Specifies the directory to generate the parser source files into.
     *
     * @param outputDirectory The output directory. Must not be null.
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @TaskAction
    void compile() {
        TwirlCompileSpec spec = generateSpec();
        getCompiler().execute(spec);
    }

    /**
     * For now just using InProcessCompilerDaemon.
     *
     * TODO allow forked compiler
     * */
    private DaemonTwirlCompiler getCompiler() {
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory = getServices().get(InProcessCompilerDaemonFactory.class);
        TwirlCompiler twirlCompiler = new TwirlCompiler();
        return new DaemonTwirlCompiler(projectInternal.getProjectDir(), twirlCompiler, inProcessCompilerDaemonFactory, getCompilerClasspath().getFiles());

    }

    private TwirlCompileSpec generateSpec() {
        return new TwirlCompileSpec(getSource().getFiles(), getOutputDirectory());
    }
}
