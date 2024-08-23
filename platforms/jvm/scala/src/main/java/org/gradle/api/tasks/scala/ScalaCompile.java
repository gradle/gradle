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

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.daemon.ProcessIsolatedCompilerWorkerExecutor;
import org.gradle.api.internal.tasks.scala.ScalaCompilerFactory;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.scala.internal.ScalaCompileOptionsConfigurer;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.language.scala.tasks.AbstractScalaCompile;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
@CacheableTask
public abstract class ScalaCompile extends AbstractScalaCompile {

    private org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> compiler;

    @Nested
    @Override
    public ScalaCompileOptions getScalaCompileOptions() {
        return (ScalaCompileOptions) super.getScalaCompileOptions();
    }

    /**
     * Returns the classpath to use to load the Scala compiler.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getScalaClasspath();

    /**
     * Returns the Scala compiler plugins to use.
     *
     * @since 6.4
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getScalaCompilerPlugins();

    @Override
    protected ScalaJavaJointCompileSpec createSpec() {
        ScalaJavaJointCompileSpec spec = super.createSpec();
        ScalaCompileOptionsConfigurer.configure(
            spec.getScalaCompileOptions(),
            getScalaCompileOptions(),
            getToolchain(),
            getScalaClasspath().getFiles()
        );
        if (getScalaCompilerPlugins() != null) {
            spec.setScalaCompilerPlugins(ImmutableList.copyOf(getScalaCompilerPlugins()));
        }
        return spec;
    }

    /**
     * Returns the classpath to use to load the Zinc incremental compiler. This compiler in turn loads the Scala compiler.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getZincClasspath();

    /**
     * For testing only.
     */
    public void setCompiler(org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    @Override
    protected org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> getCompiler(ScalaJavaJointCompileSpec spec) {
        assertScalaClasspathIsNonEmpty();
        if (compiler == null) {
            WorkerDaemonFactory workerDaemonFactory = getServices().get(WorkerDaemonFactory.class);
            JavaForkOptionsFactory forkOptionsFactory = getServices().get(JavaForkOptionsFactory.class);
            ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
            ClassLoaderRegistry classLoaderRegistry = getServices().get(ClassLoaderRegistry.class);
            ActionExecutionSpecFactory actionExecutionSpecFactory = getServices().get(ActionExecutionSpecFactory.class);
            ProjectCacheDir projectCacheDir = getServices().get(ProjectCacheDir.class);
            ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(
                getServices().get(WorkerDirectoryProvider.class).getWorkingDirectory(),
                new ProcessIsolatedCompilerWorkerExecutor(workerDaemonFactory, actionExecutionSpecFactory, projectCacheDir), getScalaClasspath(),
                getZincClasspath(), forkOptionsFactory, classPathRegistry, classLoaderRegistry,
                getServices().get(ClasspathHasher.class));
            compiler = scalaCompilerFactory.newCompiler(spec);
        }
        return compiler;
    }

    protected void assertScalaClasspathIsNonEmpty() {
        if (getScalaClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".scalaClasspath' must not be empty. If a Scala compile dependency is provided, "
                    + "the 'scala-base' plugin will attempt to configure 'scalaClasspath' automatically. Alternatively, you may configure 'scalaClasspath' explicitly.");
        }
    }
}
