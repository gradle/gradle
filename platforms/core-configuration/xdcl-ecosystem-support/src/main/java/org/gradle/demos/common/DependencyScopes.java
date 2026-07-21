/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.common;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.demos.common.dsl.Dependencies;
import org.gradle.demos.common.dsl.HasDependencies;

import java.util.List;

/**
 * Shared build logic for the {@code HasDependencies} capability: hand-rolls the slice of
 * {@code JavaBasePlugin} the demos need — the four dependency-scope configurations
 * ({@code api}/{@code implementation}/{@code compileOnly}/{@code runtimeOnly}), the per-source-set
 * scopes that extend them, and the resolvable compile/runtime classpaths.
 *
 * <p>Every JVM project-type template and every source-set record is declared {@code with … &
 * HasDependencies}, so both their facades expose {@code dependencies()} and the same wiring serves the
 * project-wide scopes and each source set's scopes — used identically by {@code JavaLibraryReaction}
 * and {@code GroovyLibraryReaction}.
 */
public final class DependencyScopes {

    /** The dependency scopes, in the order the demos wire them. */
    private static final List<String> SCOPES = List.of("api", "implementation", "compileOnly", "runtimeOnly");

    private DependencyScopes() {
    }

    /**
     * Create the four shared (top-level) dependency-scope configurations and add the project-wide
     * {@code dependencies} to them. Each source set's own scopes extend these (see
     * {@link #configureSource}).
     */
    public static void createShared(HasDependencies data, Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        for (String scope : SCOPES) {
            configurations.dependencyScope(scope);
        }
        data.dependencies().ifPresent(dependencies -> addScopes(project, "", dependencies));
    }

    /**
     * Create a source set's own dependency-scope configurations ({@code <name>Api},
     * {@code <name>Implementation}, …), each extending the matching top-level scope; add the source's
     * {@code dependencies}; and return its resolvable compile classpath (a {@code <name>RuntimeClasspath}
     * is also created for the runtime/test path).
     */
    public static FileCollection configureSource(Project project, String name, HasDependencies source) {
        ConfigurationContainer configurations = project.getConfigurations();
        for (String scope : SCOPES) {
            configurations.dependencyScope(scopeName(name, scope), c -> c.extendsFrom(configurations.getByName(scope)));
        }
        source.dependencies().ifPresent(dependencies -> addScopes(project, name, dependencies));

        Configuration compileClasspath = configurations.resolvable(name + "CompileClasspath", c -> {
            c.extendsFrom(
                configurations.getByName(scopeName(name, "api")),
                configurations.getByName(scopeName(name, "implementation")),
                configurations.getByName(scopeName(name, "compileOnly")));
            c.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API)));
        }).get();
        configurations.resolvable(name + "RuntimeClasspath", c -> {
            c.extendsFrom(
                configurations.getByName(scopeName(name, "api")),
                configurations.getByName(scopeName(name, "implementation")),
                configurations.getByName(scopeName(name, "runtimeOnly")));
            c.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME)));
        });
        return compileClasspath;
    }

    /** Add a {@link Dependencies} block's coordinates to the scope configurations under [prefix] ("" = top level). */
    private static void addScopes(Project project, String prefix, Dependencies dependencies) {
        addAll(project, scopeName(prefix, "api"), dependencies.api());
        addAll(project, scopeName(prefix, "implementation"), dependencies.implementation());
        addAll(project, scopeName(prefix, "runtimeOnly"), dependencies.runtimeOnly());
        addAll(project, scopeName(prefix, "compileOnly"), dependencies.compileOnly());
    }

    /** The configuration name for a dependency scope: bare at the top level, {@code <prefix><Scope>} per source. */
    private static String scopeName(String prefix, String scope) {
        return prefix.isEmpty() ? scope : prefix + capitalize(scope);
    }

    private static void addAll(Project project, String configuration, Provider<List<String>> notations) {
        for (String notation : notations.getOrElse(List.of())) {
            project.getDependencies().add(configuration, notation);
        }
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
