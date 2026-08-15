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

package org.gradle.xdcl.ecosystem.plugindev;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.xdcl.ecosystem.common.dsl.HasDependencies;

import java.util.List;

/**
 * Adds the declared {@code dependencies} to the real configurations {@code java-library} created
 * (rather than creating ecosystem-owned dependency scopes, which would collide with them). Shared
 * by the Java and Kotlin plugin-development reactions.
 */
final class DeclaredDependencies {

    private DeclaredDependencies() {
    }

    static void configure(HasDependencies data, Project project) {
        data.dependencies().ifPresent(dependencies -> {
            addAll(project, "api", dependencies.api());
            addAll(project, "implementation", dependencies.implementation());
            addAll(project, "runtimeOnly", dependencies.runtimeOnly());
            addAll(project, "compileOnly", dependencies.compileOnly());
        });
    }

    private static void addAll(Project project, String configuration, Provider<List<String>> notations) {
        for (String notation : notations.getOrElse(List.of())) {
            project.getDependencies().add(configuration, notation);
        }
    }
}
