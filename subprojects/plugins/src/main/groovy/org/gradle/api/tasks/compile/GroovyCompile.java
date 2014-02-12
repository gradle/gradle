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

import org.gradle.api.AntBuilder;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.*;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
public class GroovyCompile extends AbstractCompile {
    private Compiler<GroovyJavaJointCompileSpec> compiler;
    private FileCollection groovyClasspath;
    private final CompileOptions compileOptions = new CompileOptions();
    private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
    private final TemporaryFileProvider tempFileProvider;

    public GroovyCompile() {
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
        ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
        Factory<AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        JavaCompilerFactory inProcessCompilerFactory = new InProcessJavaCompilerFactory();
        tempFileProvider = projectInternal.getServices().get(TemporaryFileProvider.class);
        CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
        InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory = getServices().get(InProcessCompilerDaemonFactory.class);
        DefaultJavaCompilerFactory javaCompilerFactory = new DefaultJavaCompilerFactory(projectInternal, antBuilderFactory, inProcessCompilerFactory, compilerDaemonManager);
        GroovyCompilerFactory groovyCompilerFactory = new GroovyCompilerFactory(projectInternal, antBuilder, classPathRegistry, javaCompilerFactory, compilerDaemonManager, inProcessCompilerDaemonFactory);
        Compiler<GroovyJavaJointCompileSpec> delegatingCompiler = new DelegatingGroovyCompiler(groovyCompilerFactory);
        compiler = new CleaningGroovyCompiler(delegatingCompiler, getOutputs());
    }

    @TaskAction
    protected void compile() {
        checkGroovyClasspathIsNonEmpty();
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(getClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setGroovyClasspath(getGroovyClasspath());
        spec.setCompileOptions(compileOptions);
        spec.setGroovyCompileOptions(groovyCompileOptions);
        if (spec.getGroovyCompileOptions().getStubDir() == null) {
            File dir = tempFileProvider.newTemporaryFile("groovy-java-stubs");
            GFileUtils.mkdirs(dir);
            spec.getGroovyCompileOptions().setStubDir(dir);
        }
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    private void checkGroovyClasspathIsNonEmpty() {
        if (getGroovyClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".groovyClasspath' must not be empty. If a Groovy compile dependency is provided, "
                    + "the 'groovy-base' plugin will attempt to configure 'groovyClasspath' automatically. Alternatively, you may configure 'groovyClasspath' explicitly.");
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
        return groovyCompileOptions;
    }

    /**
     * Returns the options for Java compilation.
     *
     * @return The Java compile options. Never returns null.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
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

    public Compiler<GroovyJavaJointCompileSpec> getCompiler() {
        return compiler;
    }

    public void setCompiler(Compiler<GroovyJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }
}
