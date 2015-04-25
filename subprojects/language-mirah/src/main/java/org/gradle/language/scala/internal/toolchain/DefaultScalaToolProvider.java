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

package org.gradle.language.mirah.internal.toolchain;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.mirah.DaemonMirahCompiler;
import org.gradle.api.internal.tasks.mirah.NormalizingMirahCompiler;
import org.gradle.api.internal.tasks.mirah.MirahJavaJointCompileSpec;
import org.gradle.api.internal.tasks.mirah.ZincMirahCompiler;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.Set;

public class DefaultMirahToolProvider implements ToolProvider {
    public static final String DEFAULT_ZINC_VERSION = "0.3.5.3";

    private ProjectFinder projectFinder;
    private final CompilerDaemonManager compilerDaemonManager;
    private final Set<File> resolvedMirahClasspath;
    private final Set<File> resolvedZincClasspath;

    public DefaultMirahToolProvider(ProjectFinder projectFinder, CompilerDaemonManager compilerDaemonManager, Set<File> resolvedMirahClasspath, Set<File> resolvedZincClasspath) {
        this.projectFinder = projectFinder;
        this.compilerDaemonManager = compilerDaemonManager;
        this.resolvedMirahClasspath = resolvedMirahClasspath;
        this.resolvedZincClasspath = resolvedZincClasspath;
    }

    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (MirahJavaJointCompileSpec.class.isAssignableFrom(spec)) {
            File projectDir = projectFinder.getProject(":").getProjectDir();
            Compiler<MirahJavaJointCompileSpec> mirahCompiler = new ZincMirahCompiler(resolvedMirahClasspath, resolvedZincClasspath);
            return (Compiler<T>) new NormalizingMirahCompiler(new DaemonMirahCompiler<MirahJavaJointCompileSpec>(projectDir, mirahCompiler, compilerDaemonManager, resolvedZincClasspath));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {

    }
}
