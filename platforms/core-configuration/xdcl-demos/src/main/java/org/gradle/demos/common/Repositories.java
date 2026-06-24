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
import org.gradle.demos.common.dsl.HasRepositories;
import org.gradle.demos.common.dsl.NamedRepository;
import org.gradle.demos.common.dsl.Repository;

import java.util.List;

/**
 * Shared build logic for the {@code HasRepositories} capability: configures a project's repositories
 * from the declared notations. Both {@code JavaLibraryReaction} and {@code GroovyLibraryReaction}
 * delegate here — their facades implement {@link HasRepositories} (each template is declared
 * {@code with … & HasRepositories}), so the wiring lives once.
 */
public final class Repositories {

    private Repositories() {
    }

    /**
     * Configure the project's repositories from the declared notations: a {@code :mavenCentral} /
     * {@code :gradlePluginPortal} builtin symbol maps to the matching builtin repository; a String is
     * a maven repository URL.
     */
    public static void configure(HasRepositories data, Project project) {
        for (Repository repository : data.repositories().getOrElse(List.of())) {
            if (repository instanceof NamedRepository named) {
                String symbol = named.value().get();
                switch (symbol) {
                    case "mavenCentral" -> project.getRepositories().mavenCentral();
                    case "gradlePluginPortal" -> project.getRepositories().gradlePluginPortal();
                    default -> throw new IllegalArgumentException("unknown builtin repository: " + symbol);
                }
            } else if (repository instanceof Repository.StringValue url) {
                project.getRepositories().maven(repo -> repo.setUrl(url.value().get()));
            }
        }
    }
}
