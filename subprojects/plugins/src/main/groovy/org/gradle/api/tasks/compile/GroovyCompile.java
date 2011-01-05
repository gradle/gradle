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

package org.gradle.api.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.AntGroovyCompiler;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompiler;
import org.gradle.api.internal.tasks.compile.IncrementalGroovyCompiler;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.WorkResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 *
 * @author Hans Dockter
 */
public class GroovyCompile extends AbstractCompile {
    private GroovyJavaJointCompiler compiler;

    private FileCollection groovyClasspath;

    public GroovyCompile() {
        IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
        ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
        compiler = new IncrementalGroovyCompiler(new AntGroovyCompiler(antBuilder, classPathRegistry), getOutputs());
    }

    protected void compile() {
        List<File> taskClasspath = new ArrayList<File>(getGroovyClasspath().getFiles());
        throwExceptionIfTaskClasspathIsEmpty(taskClasspath);
        compiler.setSource(getSource());
        compiler.setDestinationDir(getDestinationDir());
        compiler.setClasspath(getClasspath());
        compiler.setSourceCompatibility(getSourceCompatibility());
        compiler.setTargetCompatibility(getTargetCompatibility());
        compiler.setGroovyClasspath(taskClasspath);
        WorkResult result = compiler.execute();
        setDidWork(result.getDidWork());
    }

    private void throwExceptionIfTaskClasspathIsEmpty(Collection<File> taskClasspath) {
        if (taskClasspath.size() == 0) {
            throw new InvalidUserDataException("You must assign a Groovy library to the groovy configuration!");
        }
    }

    /**
     * Gets the options for the Groovy compilation. To set specific options for the nested Java compilation, use {@link
     * #getOptions()}.
     *
     * @return The Groovy compile options. Never returns null.
     */
    @Nested
    public GroovyCompileOptions getGroovyOptions() {
        return compiler.getGroovyCompileOptions();
    }

    /**
     * Returns the options for Java compilation.
     *
     * @return The java compile options. Never returns null.
     */
    @Nested
    public CompileOptions getOptions() {
        return compiler.getCompileOptions();
    }

    /**
     * Returns the classpath containing the version of Groovy to use for compilation.
     *
     * @return The classpath.
     */
    @InputFiles
    public FileCollection getGroovyClasspath() {
        return groovyClasspath;
    }

    /**
     * Sets the classpath containing the version of Groovy to use for compilation.
     *
     * @param groovyClasspath The classpath. Must not be null.
     */
    public void setGroovyClasspath(FileCollection groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    public GroovyJavaJointCompiler getCompiler() {
        return compiler;
    }

    public void setCompiler(GroovyJavaJointCompiler compiler) {
        this.compiler = compiler;
    }
}
