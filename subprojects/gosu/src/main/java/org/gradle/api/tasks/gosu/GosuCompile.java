/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec;
import org.gradle.api.internal.tasks.gosu.GosuCompilerFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.gosu.tasks.AbstractGosuCompile;

import javax.inject.Inject;

/**
 * Compiles Gosu source files
 */
public class GosuCompile extends AbstractGosuCompile {

    private FileCollection gosuClasspath;
    private Closure<FileCollection> orderClasspath;

    private Compiler<GosuCompileSpec> compiler;

    @Inject
    public GosuCompile() {
        super(new GosuCompileOptions());
    }

    @Nested
    @Override
    public GosuCompileOptions getGosuCompileOptions() {
        return (GosuCompileOptions) super.getGosuCompileOptions();
    }

    /**
     * Returns the classpath to use to load the Gosu compiler.
     */
    @InputFiles
    public FileCollection getGosuClasspath() {
        return gosuClasspath;
    }

    public void setGosuClasspath(FileCollection gosuClasspath) {
        this.gosuClasspath = gosuClasspath;
    }

    @Override
    public Closure<FileCollection> getOrderClasspath() {
        return orderClasspath;
    }

    @Override
    public void setOrderClasspath(Closure<FileCollection> orderClasspath) {
        this.orderClasspath = orderClasspath;
    }

    /**
     * For testing only.
     */
    public void setCompiler(org.gradle.language.base.internal.compile.Compiler<GosuCompileSpec> compiler) {
        this.compiler = compiler;
    }

    protected Compiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec) {
        assertGosuClasspathIsNonEmpty();
        if(compiler == null) {
            ProjectInternal projectInternal = (ProjectInternal) getProject();
            IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
            CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
            GosuCompilerFactory gosuCompilerFactory = new GosuCompilerFactory(this.getPath(), projectInternal.getRootProject().getProjectDir(), antBuilder, compilerDaemonManager, getGosuClasspath(), getProject().getGradle().getGradleUserHomeDir());
            compiler = gosuCompilerFactory.newCompiler(spec);
        }
        return compiler;
    }

    protected void assertGosuClasspathIsNonEmpty() {
        if (getGosuClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".gosuClasspath' must not be empty. If a Gosu compile dependency is provided, "
                + "the 'gosu-base' plugin will attempt to configure 'gosuClasspath' automatically. Alternatively, you may configure 'gosuClasspath' explicitly.");
        }
    }
}
