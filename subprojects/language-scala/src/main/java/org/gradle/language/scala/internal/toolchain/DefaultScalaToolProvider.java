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

package org.gradle.language.scala.internal.toolchain;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.scala.DaemonScalaCompiler;
import org.gradle.api.internal.tasks.scala.HashedClasspath;
import org.gradle.api.internal.tasks.scala.NormalizingScalaCompiler;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.internal.tasks.scala.ZincScalaCompilerFacade;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

public class DefaultScalaToolProvider implements ToolProvider {
    public static final String DEFAULT_ZINC_VERSION = "1.3.5";

    private final File daemonWorkingDir;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final HashedClasspath resolvedScalaClasspath;
    private final Set<File> resolvedZincClasspath;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;

    public DefaultScalaToolProvider(File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory, JavaForkOptionsFactory forkOptionsFactory, HashedClasspath resolvedScalaClasspath, Set<File> resolvedZincClasspath, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.forkOptionsFactory = forkOptionsFactory;
        this.resolvedScalaClasspath = resolvedScalaClasspath;
        this.resolvedZincClasspath = resolvedZincClasspath;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (ScalaJavaJointCompileSpec.class.isAssignableFrom(spec)) {
            return (Compiler<T>) new NormalizingScalaCompiler(
                    new DaemonScalaCompiler<ScalaJavaJointCompileSpec>(
                            daemonWorkingDir,
                            ZincScalaCompilerFacade.class,
                            new Object[]{resolvedScalaClasspath, true},
                            workerDaemonFactory,
                            resolvedZincClasspath,
                            forkOptionsFactory,
                            classPathRegistry,
                            classLoaderRegistry,
                            actionExecutionSpecFactory
                    )
            );
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {

    }
}
