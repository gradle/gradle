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

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SecondParam;
import groovy.transform.stc.SimpleType;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.Transformers;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to add extra methods to {@link DependencyAdder} for the Groovy DSL to make the DSL more idiomatic.
 * This is implemented as a <a href="https://groovy-lang.org/metaprogramming.html#_extension_modules">Groovy Extension Module</a>.
 * <p>
 * These extension methods allow an interface exposing an instance of {@code DependencyAdder} to add dependencies without explicitly calling {@code add(...)}.
 * </p>
 * For example:
 * <pre>
 * interface MyDependencies {
 *     DependencyAdder getImplementation()
 * }
 * // In the build script
 * myDependencies {
 *     implementation "org:foo:1.0"
 * }
 * </pre>
 *
 * In this Groovy DSL example, {@code implementation "org:foo:1.0"}
 * <ul>
 *     <li>is equivalent to {@code getImplementation().call("org:foo:1.0")} because of this extension</li>
 *     <li>has the same effect as {@code getImplementation().add("org:foo:1.0")} in Java</li>
 * </ul>
 *
 * There are {@code call(...)} equivalents for all the {@code add(...)} methods in {@code DependencyAdder}.
 *
 * @see DependencyAdder
 * @see DependencyFactory
 * @see Dependencies
 */
@SuppressWarnings("unused")
public class DependencyAdderExtensionModule {
    private static final String GROUP = "group";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final Set<String> MODULE_LEGAL_MAP_KEYS = ImmutableSet.of(GROUP, NAME, VERSION);

    /**
     * Creates an {@link ExternalModuleDependency} from the given Map notation. This emulates named parameters in Groovy DSL.
     * <p>
     * The map may contain:
     * <ul>
     *   <li>{@code group}</li>
     *   <li>{@code version}</li>
     * </ul>
     *
     * It must contain at least the following keys:
     * <ul>
     *   <li>{@code name}</li>
     * </ul>
     *
     * @param map a map of configuration parameters for the dependency
     *
     * @return the dependency
     */
    public static ExternalModuleDependency module(Dependencies self, Map<String, CharSequence> map) {
        if (!MODULE_LEGAL_MAP_KEYS.containsAll(map.keySet())) {
            CollectionUtils.SetDiff<String> diff = CollectionUtils.diffSetsBy(MODULE_LEGAL_MAP_KEYS, map.keySet(), Transformers.noOpTransformer());
            throw new IllegalArgumentException("The map must not contain the following keys: " + diff.rightOnly);
        }
        if (!map.containsKey(NAME)) {
            throw new IllegalArgumentException("The map must contain a name key.");
        }
        String group = extract(map, GROUP);
        String name = extract(map, NAME);
        String version = extract(map, VERSION);
        return self.module(group, name, version);
    }

    @Nullable
    private static String extract(Map<String, CharSequence> map, String key) {
        return (map.containsKey(key)) ? map.get(key).toString() : null;
    }

    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation) {
        self.add(dependencyNotation);
    }

    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(dependencyNotation, ConfigureUtil.configureUsing(configuration));
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
