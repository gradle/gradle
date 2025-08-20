/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.GradleApiImplicitImportsProvider;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.classpath.GradleApiClasspathProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.tooling.model.dsl.DslBaseScriptModel;
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Stream;

@NullMarked
public class DslBaseScriptModelBuilder implements BuildScopeModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return "org.gradle.tooling.model.dsl.DslBaseScriptModel".equals(modelName);
    }

    @Override
    public @Nullable Object create(BuildState target) {
        GradleInternal gradle = target.getMutableModel();
        ModuleRegistry moduleRegistry = gradle.getServices().get(ModuleRegistry.class);
        GradleApiImplicitImportsProvider implicitImports = gradle.getServices().get(GradleApiImplicitImportsProvider.class);
        GradleApiClasspathProvider apiClasspathProvider = gradle.getServices().get(GradleApiClasspathProvider.class);
        DependencyFactoryInternal dependencyFactory = gradle.getServices().get(DependencyFactoryInternal.class);
        return new StandardDslBaseScriptModel(
            getGradleApiClassPath(dependencyFactory).getAsFiles(),
            getScriptTemplatesClassPath(moduleRegistry).getAsFiles(),
            apiClasspathProvider.getGradleKotlinDslApi().getAsFiles(),
            implicitImports.getKotlinDslImplicitImports()
        );
    }

    @Nonnull
    private static ClassPath getGradleApiClassPath(DependencyFactoryInternal dependencyFactory) {
        return DefaultClassPath.of(((FileCollectionDependency) dependencyFactory.gradleApi()).getFiles().getFiles());
    }

    private static ClassPath getScriptTemplatesClassPath(ModuleRegistry moduleRegistry) {
        return Stream.of("gradle-core", "gradle-tooling-api")
            .map(moduleRegistry::getModule)
            .flatMap(it -> it.getAllRequiredModules().stream())
            .reduce(ClassPath.EMPTY, (classPath, module) -> classPath.plus(module.getClasspath()), ClassPath::plus);
    }
}

@NullMarked
class StandardDslBaseScriptModel implements DslBaseScriptModel, Serializable {

    private final List<File> gradleApiClassPath;
    private final List<File> scriptTemplatesClassPath;
    private final List<String> implicitImports;
    private final List<File> kotlinDslClassPath;

    public StandardDslBaseScriptModel(List<File> gradleApiClassPath, List<File> scriptTemplatesClassPath, List<File> kotlinDslClassPath, List<String> implicitImports) {
        this.gradleApiClassPath = gradleApiClassPath;
        this.scriptTemplatesClassPath = scriptTemplatesClassPath;
        this.kotlinDslClassPath = kotlinDslClassPath;
        this.implicitImports = implicitImports;
    }

    @Override
    public List<File> getGradleApiClassPath() {
        return gradleApiClassPath;
    }

    @Override
    public List<File> getScriptTemplatesClassPath() {
        return scriptTemplatesClassPath;
    }

    @Override
    public List<String> getKotlinScriptImplicitImports() {
        return implicitImports;
    }

    @Override
    public List<File> getKotlinDslClassPath() {
        return kotlinDslClassPath;
    }
}
