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

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.scala.DaemonScalaCompiler;
import org.gradle.api.internal.tasks.scala.NormalizingScalaCompiler;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.Set;

public class DefaultScalaToolProvider implements ToolProvider {
    public static final String DEFAULT_ZINC_VERSION = "0.3.0";

    private ProjectFinder projectFinder;
    private final CompilerDaemonManager compilerDaemonManager;
    private final Set<File> resolvedScalaClasspath;
    private final Set<File> resolvedZincClasspath;

    public DefaultScalaToolProvider(ProjectFinder projectFinder, CompilerDaemonManager compilerDaemonManager, Set<File> resolvedScalaClasspath, Set<File> resolvedZincClasspath) {
        this.projectFinder = projectFinder;
        this.compilerDaemonManager = compilerDaemonManager;
        this.resolvedScalaClasspath = resolvedScalaClasspath;
        this.resolvedZincClasspath = resolvedZincClasspath;
    }

    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(T spec) {
        if (spec instanceof ScalaJavaJointCompileSpec) {
            File projectDir = projectFinder.getProject(":").getProjectDir();
            org.gradle.language.base.internal.compile.Compiler<ScalaJavaJointCompileSpec> scalaCompiler;
            try {
                scalaCompiler = (Compiler<ScalaJavaJointCompileSpec>) getClass().getClassLoader()
                        .loadClass("org.gradle.api.internal.tasks.scala.jdk6.ZincScalaCompiler").getConstructor(Iterable.class, Iterable.class).newInstance(resolvedScalaClasspath, resolvedZincClasspath);
            } catch (Exception e) {
                throw new RuntimeException("Internal error: Failed to load org.gradle.api.internal.tasks.scala.jdk6.ZincScalaCompiler", e);
            }

            return (Compiler<T>) new NormalizingScalaCompiler(new DaemonScalaCompiler<ScalaJavaJointCompileSpec>(projectDir, scalaCompiler, compilerDaemonManager, resolvedZincClasspath));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getClass().getSimpleName()));
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {

    }
}
