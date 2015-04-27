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
package org.gradle.api.tasks.mirah;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.mirah.CleaningMirahCompiler;
import org.gradle.api.internal.tasks.mirah.MirahCompileSpec;
import org.gradle.api.internal.tasks.mirah.MirahCompilerFactory;
import org.gradle.api.internal.tasks.mirah.MirahCompileSpec;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.language.mirah.tasks.AbstractMirahCompile;

import javax.inject.Inject;

/**
 * Compiles Mirah source files, and optionally, Java source files.
 */
public class MirahCompile extends AbstractMirahCompile {

    private FileCollection mirahClasspath;

    private org.gradle.language.base.internal.compile.Compiler<MirahCompileSpec> compiler;

    @Inject
    public MirahCompile() {
        super(new MirahCompileOptions());
    }

    @Nested
    @Override
    public MirahCompileOptions getMirahCompileOptions() {
        return (MirahCompileOptions) super.getMirahCompileOptions();
    }

    /**
     * Returns the classpath to use to load the Mirah compiler.
     */
    @InputFiles
    public FileCollection getMirahClasspath() {
        return mirahClasspath;
    }

    public void setMirahClasspath(FileCollection mirahClasspath) {
        this.mirahClasspath = mirahClasspath;
    }

    /**
     * For testing only.
     */
    public void setCompiler(org.gradle.language.base.internal.compile.Compiler<MirahCompileSpec> compiler) {
        this.compiler = compiler;
    }

    protected org.gradle.language.base.internal.compile.Compiler<MirahCompileSpec> getCompiler(MirahCompileSpec spec) {
        assertMirahClasspathIsNonEmpty();
        if (compiler == null) {
            ProjectInternal projectInternal = (ProjectInternal) getProject();
            IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
            CompilerDaemonFactory compilerDaemonFactory = getServices().get(CompilerDaemonManager.class);
            JavaCompilerFactory javaCompilerFactory = getServices().get(JavaCompilerFactory.class);
            MirahCompilerFactory mirahCompilerFactory = new MirahCompilerFactory(projectInternal.getRootProject().getProjectDir(), antBuilder, javaCompilerFactory, compilerDaemonFactory, getMirahClasspath());
            compiler = mirahCompilerFactory.newCompiler(spec);
            if (getMirahCompileOptions().isUseAnt()) {
                compiler = new CleaningMirahCompiler(compiler, getOutputs());
            }
        }
        return compiler;
    }

    @Override
    protected void configureIncrementalCompilation(MirahCompileSpec spec) {
        if (getMirahCompileOptions().isUseAnt()) {
            // Don't use incremental compilation with ant-backed compiler
            return;
        }
        super.configureIncrementalCompilation(spec);
    }


    protected void assertMirahClasspathIsNonEmpty() {
        if (getMirahClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".mirahClasspath' must not be empty. If a Mirah compile dependency is provided, "
                    + "the 'mirah-base' plugin will attempt to configure 'mirahClasspath' automatically. Alternatively, you may configure 'mirahClasspath' explicitly.");
        }
    }
}
