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

package org.gradle.play.internal.toolchain;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.javascript.GoogleClosureCompiler;
import org.gradle.play.internal.javascript.JavaScriptCompileSpec;
import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.internal.routes.RoutesCompileSpec;
import org.gradle.play.internal.routes.RoutesCompiler;
import org.gradle.play.internal.routes.RoutesCompilerFactory;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.run.PlayApplicationRunnerFactory;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompiler;
import org.gradle.play.internal.twirl.TwirlCompilerFactory;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.util.TreeVisitor;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

class DefaultPlayToolProvider implements PlayToolProvider {

    private final FileResolver fileResolver;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final File daemonWorkingDir;
    private final PlayPlatform targetPlatform;
    private WorkerProcessFactory workerProcessBuilderFactory;
    private final Set<File> twirlClasspath;
    private final Set<File> routesClasspath;
    private final Set<File> javaScriptClasspath;

    public DefaultPlayToolProvider(FileResolver fileResolver, File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory,
                                   WorkerProcessFactory workerProcessBuilderFactory, PlayPlatform targetPlatform,
                                   Set<File> twirlClasspath, Set<File> routesClasspath, Set<File> javaScriptClasspath) {
        this.fileResolver = fileResolver;
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
        this.targetPlatform = targetPlatform;
        this.twirlClasspath = twirlClasspath;
        this.routesClasspath = routesClasspath;
        this.javaScriptClasspath = javaScriptClasspath;
        // validate that the targetPlatform is valid
        PlayMajorVersion.forPlatform(targetPlatform);
    }

    @Override
    public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> spec) {
        if (TwirlCompileSpec.class.isAssignableFrom(spec)) {
            TwirlCompiler twirlCompiler = TwirlCompilerFactory.create(targetPlatform);
            return cast(new DaemonPlayCompiler<TwirlCompileSpec>(daemonWorkingDir, twirlCompiler, workerDaemonFactory, twirlClasspath, twirlCompiler.getClassLoaderPackages(), fileResolver));
        } else if (RoutesCompileSpec.class.isAssignableFrom(spec)) {
            RoutesCompiler routesCompiler = RoutesCompilerFactory.create(targetPlatform);
            return cast(new DaemonPlayCompiler<RoutesCompileSpec>(daemonWorkingDir, routesCompiler, workerDaemonFactory, routesClasspath, routesCompiler.getClassLoaderPackages(), fileResolver));
        } else if (JavaScriptCompileSpec.class.isAssignableFrom(spec)) {
            GoogleClosureCompiler javaScriptCompiler = new GoogleClosureCompiler();
            return cast(new DaemonPlayCompiler<JavaScriptCompileSpec>(daemonWorkingDir, javaScriptCompiler, workerDaemonFactory, javaScriptClasspath, javaScriptCompiler.getClassLoaderPackages(), fileResolver));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    @Override
    public <T> T get(Class<T> toolType) {
        if (PlayApplicationRunner.class.isAssignableFrom(toolType)) {
            return toolType.cast(PlayApplicationRunnerFactory.create(targetPlatform, workerProcessBuilderFactory));
        }
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    private <T extends CompileSpec> Compiler<T> cast(Compiler<? extends PlayCompileSpec> raw) {
        @SuppressWarnings("unchecked")
        Compiler<T> converted = (Compiler<T>) raw;
        return converted;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
    }
}
