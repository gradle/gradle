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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class GradleApiVersionProvider {
    public static Optional<String> getGradleApiSourceVersion() {
        return Optional.ofNullable(System.getProperty("org.gradle.api.source-version"));
    }

    public static void addGradleSourceApiRepository(RepositoryHandler repositoryHandler) {
        getGradleApiSourceVersion().ifPresent(version -> {
            String repositoryUrl = System.getProperty("gradle.api.repository.url", "https://repo.gradle.org/gradle/libs-releases");
            repositoryHandler.maven(repo -> repo.setUrl(repositoryUrl));
        });
    }

    public static void addToConfiguration(Configuration configuration, DependencyHandler repositoryHandler) {
        Dependency gradleApiDependency = getGradleApiSourceVersion()
            .map(repositoryHandler::gradleApi)
            .orElseGet(repositoryHandler::gradleApi);
        configuration.getDependencies().add(gradleApiDependency);
    }

    public static Collection<File> resolveGradleSourceApi(DependencyResolutionServices dependencyResolutionServices) {
        return getGradleApiSourceVersion()
            .map(version -> gradleApisFromRepository(dependencyResolutionServices, version))
            .orElseGet(() -> gradleApisFromCurrentGradle(dependencyResolutionServices.getDependencyHandler()));
    }

    private static Set<File> gradleApisFromCurrentGradle(DependencyHandler dependencyHandler) {
        SelfResolvingDependency gradleApiDependency = (SelfResolvingDependency) dependencyHandler.gradleApi();
        return gradleApiDependency.resolve();

    }
    private static Set<File> gradleApisFromRepository(DependencyResolutionServices dependencyResolutionServices, String version) {
        addGradleSourceApiRepository(dependencyResolutionServices.getResolveRepositoryHandler());
        Configuration detachedConfiguration = dependencyResolutionServices.getConfigurationContainer().detachedConfiguration(dependencyResolutionServices.getDependencyHandler().gradleApi(version));
        return detachedConfiguration.resolve();
    }
}
