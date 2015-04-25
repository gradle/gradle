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
import org.gradle.api.internal.tasks.mirah.CleaningScalaCompiler;
import org.gradle.api.internal.tasks.mirah.ScalaCompileSpec;
import org.gradle.api.internal.tasks.mirah.ScalaCompilerFactory;
import org.gradle.api.internal.tasks.mirah.ScalaJavaJointCompileSpec;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.language.mirah.tasks.AbstractScalaCompile;

import javax.inject.Inject;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
public class ScalaCompile extends AbstractScalaCompile {

    private FileCollection mirahClasspath;
    private FileCollection zincClasspath;

    private org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> compiler;

    @Inject
    public ScalaCompile() {
        super(new ScalaCompileOptions());
    }

    @Nested
    @Override
    public ScalaCompileOptions getScalaCompileOptions() {
        return (ScalaCompileOptions) super.getScalaCompileOptions();
    }

    /**
     * Returns the classpath to use to load the Scala compiler.
     */
    @InputFiles
    public FileCollection getScalaClasspath() {
        return mirahClasspath;
    }

    public void setScalaClasspath(FileCollection mirahClasspath) {
        this.mirahClasspath = mirahClasspath;
    }

    /**
     * Returns the classpath to use to load the Zinc incremental compiler. This compiler in turn loads the Scala compiler.
     */
    @InputFiles
    public FileCollection getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(FileCollection zincClasspath) {
        this.zincClasspath = zincClasspath;
    }

    /**
     * For testing only.
     */
    public void setCompiler(org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    protected org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> getCompiler(ScalaJavaJointCompileSpec spec) {
        assertScalaClasspathIsNonEmpty();
        if (compiler == null) {
            ProjectInternal projectInternal = (ProjectInternal) getProject();
            IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
            CompilerDaemonFactory compilerDaemonFactory = getServices().get(CompilerDaemonManager.class);
            JavaCompilerFactory javaCompilerFactory = getServices().get(JavaCompilerFactory.class);
            ScalaCompilerFactory mirahCompilerFactory = new ScalaCompilerFactory(projectInternal.getRootProject().getProjectDir(), antBuilder, javaCompilerFactory, compilerDaemonFactory, getScalaClasspath(), getZincClasspath());
            compiler = mirahCompilerFactory.newCompiler(spec);
            if (getScalaCompileOptions().isUseAnt()) {
                compiler = new CleaningScalaCompiler(compiler, getOutputs());
            }
        }
        return compiler;
    }

    @Override
    protected void configureIncrementalCompilation(ScalaCompileSpec spec) {
        if (getScalaCompileOptions().isUseAnt()) {
            // Don't use incremental compilation with ant-backed compiler
            return;
        }
        super.configureIncrementalCompilation(spec);
    }


    protected void assertScalaClasspathIsNonEmpty() {
        if (getScalaClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".mirahClasspath' must not be empty. If a Scala compile dependency is provided, "
                    + "the 'mirah-base' plugin will attempt to configure 'mirahClasspath' automatically. Alternatively, you may configure 'mirahClasspath' explicitly.");
        }
    }
}
