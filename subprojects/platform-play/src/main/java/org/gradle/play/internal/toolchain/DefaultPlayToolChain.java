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

import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.play.internal.javascript.GoogleClosureCompiler;
import org.gradle.play.internal.routes.RoutesCompilerFactory;
import org.gradle.play.internal.twirl.TwirlCompilerFactory;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TreeVisitor;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

public class DefaultPlayToolChain implements PlayToolChainInternal {
    private FileResolver fileResolver;
    private WorkerDaemonFactory workerDaemonFactory;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final WorkerProcessFactory workerProcessBuilderFactory;
    private final WorkerDirectoryProvider workerDirectoryProvider;
    private final BuildGateToken gateToken;

    public DefaultPlayToolChain(FileResolver fileResolver, WorkerDaemonFactory workerDaemonFactory, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, WorkerProcessFactory workerProcessBuilderFactory, WorkerDirectoryProvider workerDirectoryProvider, BuildGateToken gateToken) {
        this.fileResolver = fileResolver;
        this.workerDaemonFactory = workerDaemonFactory;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
        this.workerDirectoryProvider = workerDirectoryProvider;
        this.gateToken = gateToken;
    }

    @Override
    public String getName() {
        return "PlayToolchain";
    }

    @Override
    public String getDisplayName() {
        return "Default Play Toolchain";
    }

    @Override
    public PlayToolProvider select(PlayPlatform targetPlatform) {
        try {
            Set<File> twirlClasspath = resolveToolClasspath(TwirlCompilerFactory.createAdapter(targetPlatform).getDependencyNotation()).resolve();
            Set<File> routesClasspath = resolveToolClasspath(RoutesCompilerFactory.createAdapter(targetPlatform).getDependencyNotation()).resolve();
            Set<File> javascriptClasspath = resolveToolClasspath(GoogleClosureCompiler.getDependencyNotation()).resolve();
            return new DefaultPlayToolProvider(fileResolver, gateToken, workerDirectoryProvider.getIdleWorkingDirectory(), workerDaemonFactory, workerProcessBuilderFactory, targetPlatform, twirlClasspath, routesClasspath, javascriptClasspath);
        } catch (ResolveException e) {
            return new UnavailablePlayToolProvider(e);
        }
    }

    private Configuration resolveToolClasspath(Object... dependencyNotations) {
        List<Dependency> dependencies = CollectionUtils.collect(dependencyNotations, new Transformer<Dependency, Object>() {
            public Dependency transform(Object dependencyNotation) {
                return dependencyHandler.create(dependencyNotation);
            }
        });
        Dependency[] dependenciesArray = dependencies.toArray(new Dependency[0]);
        return configurationContainer.detachedConfiguration(dependenciesArray);
    }

    private static class UnavailablePlayToolProvider implements PlayToolProvider {
        private final Exception exception;

        public UnavailablePlayToolProvider(Exception exception) {
            this.exception = exception;
        }

        @Override
        public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
            throw failure();
        }

        @Override
        public <T> T get(Class<T> toolType) {
            throw failure();
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        private RuntimeException failure() {
            TreeFormatter formatter = new TreeFormatter();
            this.explain(formatter);
            return new GradleException(formatter.toString());
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node("Cannot provide Play tool provider");
            visitor.startChildren();
            visitor.node(exception.getCause().getMessage());
            visitor.endChildren();
        }
    }
}
