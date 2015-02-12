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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.javascript.JavaScriptCompileSpec;
import org.gradle.play.internal.javascript.GoogleClosureCompiler;
import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.internal.routes.RoutesCompileSpec;
import org.gradle.play.internal.routes.RoutesCompiler;
import org.gradle.play.internal.routes.RoutesCompilerFactory;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.run.PlayRunAdapterV22X;
import org.gradle.play.internal.run.PlayRunAdapterV23X;
import org.gradle.play.internal.run.VersionedPlayRunAdapter;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompiler;
import org.gradle.play.internal.twirl.TwirlCompilerFactory;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DefaultPlayToolProvider implements PlayToolProvider {

    private final FileResolver fileResolver;
    private final CompilerDaemonManager compilerDaemonManager;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final PlayPlatform targetPlatform;
    private final PlayMajorVersion playMajorVersion;
    private Factory<WorkerProcessBuilder> workerProcessBuilderFactory;

    public DefaultPlayToolProvider(FileResolver fileResolver, CompilerDaemonManager compilerDaemonManager, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, Factory<WorkerProcessBuilder> workerProcessBuilderFactory, PlayPlatform targetPlatform) {
        this.fileResolver = fileResolver;
        this.compilerDaemonManager = compilerDaemonManager;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
        this.targetPlatform = targetPlatform;
        this.playMajorVersion = PlayMajorVersion.forPlatform(targetPlatform);
    }

    // TODO:DAZ Detangle Routes adapter from compile specs
    public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> spec) {
        if (TwirlCompileSpec.class.isAssignableFrom(spec)) {
            TwirlCompiler twirlCompiler = TwirlCompilerFactory.create(targetPlatform);
            Set<File> twirlClasspath = resolveToolClasspath(twirlCompiler.getDependencyNotation()).getFiles();
            return cast(new DaemonPlayCompiler<TwirlCompileSpec>(fileResolver.resolve("."), twirlCompiler, compilerDaemonManager, twirlClasspath, twirlCompiler.getClassLoaderPackages()));
        } else if (RoutesCompileSpec.class.isAssignableFrom(spec)) {
            RoutesCompiler routesCompiler = RoutesCompilerFactory.create(targetPlatform);
            Set<File> routesClasspath = resolveToolClasspath(routesCompiler.getDependencyNotation()).getFiles();
            return cast(new DaemonPlayCompiler<RoutesCompileSpec>(fileResolver.resolve("."), routesCompiler, compilerDaemonManager, routesClasspath, routesCompiler.getClassLoaderPackages()));
        } else if (JavaScriptCompileSpec.class.isAssignableFrom(spec)) {
            GoogleClosureCompiler javaScriptCompiler = new GoogleClosureCompiler();
            Set<File> javaScriptCompilerClasspath = resolveToolClasspath(javaScriptCompiler.getDependencyNotation()).getFiles();
            return cast(new DaemonPlayCompiler<JavaScriptCompileSpec>(fileResolver.resolve("."), javaScriptCompiler, compilerDaemonManager, javaScriptCompilerClasspath, javaScriptCompiler.getClassLoaderPackages()));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    private <T extends CompileSpec> Compiler<T> cast(Compiler<? extends PlayCompileSpec> raw) {
        @SuppressWarnings("unchecked")
        Compiler<T> converted = (Compiler<T>) raw;
        return converted;
    }

    public PlayApplicationRunner newApplicationRunner() {
        VersionedPlayRunAdapter playRunAdapter = createPlayRunAdapter();
        return new PlayApplicationRunner(fileResolver.resolve("."), workerProcessBuilderFactory, playRunAdapter);
    }


    private VersionedPlayRunAdapter createPlayRunAdapter() {
        switch (playMajorVersion) {
            case PLAY_2_2_X:
                return new PlayRunAdapterV22X();
            case PLAY_2_3_X:
            default:
                return new PlayRunAdapterV23X();
        }
    }

    private Configuration resolveToolClasspath(Object... dependencyNotations) {
        List<Dependency> dependencies = CollectionUtils.collect(dependencyNotations, new Transformer<Dependency, Object>() {
            public Dependency transform(Object dependencyNotation) {
                return dependencyHandler.create(dependencyNotation);
            }
        });
        Dependency[] dependenciesArray = dependencies.toArray(new Dependency[dependencies.size()]);
        return configurationContainer.detachedConfiguration(dependenciesArray);
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    private class MappingSpecCompiler<T extends CompileSpec, V extends T> implements Compiler<T> {
        private Compiler<V> delegate;
        private final Map<T, V> mapping;

        public MappingSpecCompiler(Compiler<V> delegate, Map<T, V> mapping) {
            this.delegate = delegate;
            this.mapping = mapping;
        }

        public WorkResult execute(T spec) {
            return delegate.execute(mapping.get(spec));
        }
    }

}
