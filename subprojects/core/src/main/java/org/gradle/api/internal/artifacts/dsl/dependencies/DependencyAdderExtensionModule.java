/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SecondParam;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.util.internal.ConfigureUtil;

import java.util.Map;

public class DependencyAdderExtensionModule {
    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @see DependencyFactory#createFromCharSequence(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation) {
        self.add(dependencyNotation);
    }

    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#createFromCharSequence(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(dependencyNotation, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param map a map of configuration parameters for the dependency
     * @see DependencyFactory#createFromMap(Map) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, Map<String, ?> map) {
        self.add(map);
    }

    /**
     * Add a dependency.
     *
     * @param map a map of configuration parameters for the dependency
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#createFromMap(Map) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, Map<String, ?> map, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(map, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param project project to add as a dependency
     */
    public static void call(DependencyAdder self, Project project) {
        self.add(project);
    }

    /**
     * Add a dependency.
     *
     * @param project project to add as a dependency
     * @param configuration an action to configure the dependency
     */
    public static void call(DependencyAdder self, Project project, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ProjectDependency") Closure<?> configuration) {
        self.add(project, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param files files to add as a dependency
     */
    public static void call(DependencyAdder self, FileCollection files) {
        self.add(files);
    }

    /**
     * Add a dependency.
     *
     * @param files files to add as a dependency
     * @param configuration an action to configure the dependency
     */
    public static void call(DependencyAdder self, FileCollection files, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.FileCollectionDependency") Closure<?> configuration) {
        self.add(files, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param externalModule external module to add as a dependency
     */
    public static void call(DependencyAdder self, ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule) {
        self.add(externalModule);
    }

    /**
     * Add a dependency.
     *
     * @param externalModule external module to add as a dependency
     * @param configuration an action to configure the dependency
     */
    public static void call(DependencyAdder self, ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(externalModule, ConfigureUtil.configureUsing(configuration));
    }


    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    public static void call(DependencyAdder self, Dependency dependency) {
        self.add(dependency);
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    public static <D extends Dependency> void call(DependencyAdder self, D dependency, @ClosureParams(SecondParam.class) Closure<?> configuration) {
        self.add(dependency, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    public static void call(DependencyAdder self, Provider<? extends Dependency> dependency) {
        self.add(dependency);
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    public static <D extends Dependency> void call(DependencyAdder self, Provider<? extends D> dependency, @ClosureParams(SecondParam.FirstGenericType.class) Closure<?> configuration) {
        self.add(dependency, ConfigureUtil.configureUsing(configuration));
    }
}
