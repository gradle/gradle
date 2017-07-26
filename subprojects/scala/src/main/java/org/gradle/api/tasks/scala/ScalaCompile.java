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
package org.gradle.api.tasks.scala;

import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec;
import org.gradle.api.internal.tasks.scala.ScalaCompilerFactory;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.language.scala.tasks.AbstractScalaCompile;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.WorkerDaemonFactory;

import javax.inject.Inject;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
@CacheableTask
public class ScalaCompile extends AbstractScalaCompile {

    private FileCollection scalaClasspath;
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
    @Classpath
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileTree getSource() {
        return super.getSource();
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    /**
     * Returns the classpath to use to load the Zinc incremental compiler. This compiler in turn loads the Scala compiler.
     */
    @Classpath
    public FileCollection getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(FileCollection zincClasspath) {
        this.zincClasspath = zincClasspath;
    }

    /**
     * The Java major version of the JVM the Scala compiler is running on.
     *
     * @since 4.1
     */
    @Incubating
    @Input
    // We track this as an input since the Scala compiler output may depend on it.
    // TODO: This should be replaced by a property in the Scala toolchain as soon as we model these.
    protected String getJvmVersion() {
        return JavaVersion.current().getMajorVersion();
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
            WorkerDaemonFactory workerDaemonFactory = getServices().get(WorkerDaemonFactory.class);
            FileResolver fileResolver = getServices().get(FileResolver.class);
            ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(
                getServices().get(WorkerDirectoryProvider.class).getIdleWorkingDirectory(), workerDaemonFactory, getScalaClasspath(),
                getZincClasspath(), getProject().getGradle().getGradleUserHomeDir(), fileResolver);
            compiler = scalaCompilerFactory.newCompiler(spec);
        }
        return compiler;
    }

    @Override
    protected void configureIncrementalCompilation(ScalaCompileSpec spec) {
        super.configureIncrementalCompilation(spec);
    }


    protected void assertScalaClasspathIsNonEmpty() {
        if (getScalaClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".scalaClasspath' must not be empty. If a Scala compile dependency is provided, "
                    + "the 'scala-base' plugin will attempt to configure 'scalaClasspath' automatically. Alternatively, you may configure 'scalaClasspath' explicitly.");
        }
    }
}
