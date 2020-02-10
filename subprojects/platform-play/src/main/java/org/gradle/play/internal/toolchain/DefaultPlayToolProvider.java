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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.javascript.GoogleClosureCompiler;
import org.gradle.play.internal.javascript.JavaScriptCompileSpec;
import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.internal.routes.RoutesCompileSpec;
import org.gradle.play.internal.routes.RoutesCompiler;
import org.gradle.play.internal.routes.RoutesCompilerAdapterFactory;
import org.gradle.play.internal.routes.VersionedRoutesCompilerAdapter;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.run.PlayApplicationRunnerFactory;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompiler;
import org.gradle.play.internal.twirl.TwirlCompilerAdapterFactory;
import org.gradle.play.internal.twirl.VersionedTwirlCompilerAdapter;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

class DefaultPlayToolProvider implements PlayToolProvider {

    private final JavaForkOptionsFactory forkOptionsFactory;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final File daemonWorkingDir;
    private final PlayPlatform targetPlatform;
    private WorkerProcessFactory workerProcessBuilderFactory;
    private final Set<File> twirlClasspath;
    private final Set<File> routesClasspath;
    private final Set<File> javaScriptClasspath;
    private final FileCollectionFingerprinter fingerprinter;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final FileCollectionFactory fileCollectionFactory;

    public DefaultPlayToolProvider(JavaForkOptionsFactory forkOptionsFactory, File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory,
                                   WorkerProcessFactory workerProcessBuilderFactory, PlayPlatform targetPlatform,
                                   Set<File> twirlClasspath, Set<File> routesClasspath, Set<File> javaScriptClasspath,
                                   FileCollectionFingerprinter fingerprinter, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry,
                                   ActionExecutionSpecFactory actionExecutionSpecFactory, FileCollectionFactory fileCollectionFactory) {
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
        this.targetPlatform = targetPlatform;
        this.twirlClasspath = twirlClasspath;
        this.routesClasspath = routesClasspath;
        this.javaScriptClasspath = javaScriptClasspath;
        this.fingerprinter = fingerprinter;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        // validate that the targetPlatform is valid
        PlayMajorVersion.forPlatform(targetPlatform);
    }

    @Override
    public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> spec) {
        if (TwirlCompileSpec.class.isAssignableFrom(spec)) {
            VersionedTwirlCompilerAdapter adapter = TwirlCompilerAdapterFactory.createAdapter(targetPlatform);
            return cast(new DaemonPlayCompiler<>(daemonWorkingDir, TwirlCompiler.class, new Object[] {adapter}, workerDaemonFactory, twirlClasspath, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory));
        } else if (RoutesCompileSpec.class.isAssignableFrom(spec)) {
            VersionedRoutesCompilerAdapter adapter = RoutesCompilerAdapterFactory.createAdapter(targetPlatform);
            return cast(new DaemonPlayCompiler<>(daemonWorkingDir, RoutesCompiler.class, new Object[] {adapter}, workerDaemonFactory, routesClasspath, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory));
        } else if (JavaScriptCompileSpec.class.isAssignableFrom(spec)) {
            return cast(new DaemonPlayCompiler<>(daemonWorkingDir, GoogleClosureCompiler.class, new Object[] {}, workerDaemonFactory, javaScriptClasspath, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    @Override
    public <T> T get(Class<T> toolType) {
        if (PlayApplicationRunner.class.isAssignableFrom(toolType)) {
            return toolType.cast(PlayApplicationRunnerFactory.create(targetPlatform, workerProcessBuilderFactory, fingerprinter, fileCollectionFactory));
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
    public void explain(DiagnosticsVisitor visitor) {
    }
}
