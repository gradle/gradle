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

package org.gradle.xdl.ecosystem.plugindev;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Provider;
import org.gradle.xdl.ecosystem.common.dsl.Dependencies;
import org.gradle.xdl.ecosystem.common.dsl.DependencyDeclaration;
import org.gradle.xdl.ecosystem.common.dsl.HasDependencies;
import org.gradle.xdl.ecosystem.common.dsl.ProjectDependencyDeclaration;

import java.util.Collections;
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
        Dependencies dependencies = data.dependencies().getOrNull();
        if (dependencies == null) {
            return;
        }
        addAll(project, "api", dependencies.api());
        addAll(project, "implementation", dependencies.implementation());
        addAll(project, "runtimeOnly", dependencies.runtimeOnly());
        addAll(project, "compileOnly", dependencies.compileOnly());
    }

    private static void addAll(Project project, String configuration, Provider<List<DependencyDeclaration>> declared) {
        DependencyHandler handler = project.getDependencies();
        for (DependencyDeclaration declaration : declared.getOrElse(Collections.emptyList())) {
            handler.add(configuration, notationOf(declaration, handler));
        }
    }

    /**
     * The notation {@link DependencyHandler#add} takes for one declaration: a coordinate string as
     * written, or a project dependency built from its path (whether that project exists is
     * reported by resolution, as for any project dependency).
     */
    private static Object notationOf(DependencyDeclaration declaration, DependencyHandler handler) {
        if (declaration instanceof DependencyDeclaration.StringValue) {
            return ((DependencyDeclaration.StringValue) declaration).value();
        }
        if (declaration instanceof ProjectDependencyDeclaration) {
            String path = ((ProjectDependencyDeclaration) declaration).project().get();
            return handler.project(Collections.singletonMap("path", path));
        }
        throw new IllegalStateException("Unknown dependency declaration: " + declaration);
    }
}
