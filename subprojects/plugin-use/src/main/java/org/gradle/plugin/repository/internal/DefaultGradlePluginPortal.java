/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.plugin.repository.GradlePluginPortal;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.PluginResolutionServiceResolver;

class DefaultGradlePluginPortal implements GradlePluginPortal, PluginRepositoryInternal, BackedByArtifactRepository {
    private PluginResolutionServiceResolver pluginResolutionServiceResolver;

    DefaultGradlePluginPortal(PluginResolutionServiceResolver pluginResolutionServiceResolver) {
        this.pluginResolutionServiceResolver = pluginResolutionServiceResolver;
    }

    @Override
    public PluginResolver asResolver() {
        return pluginResolutionServiceResolver;
    }

    @Override
    public ArtifactRepository createArtifactRepository(RepositoryHandler repositoryHandler) {
        return repositoryHandler.maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl("https://plugins.gradle.org/m2");
            }
        });
    }
}
