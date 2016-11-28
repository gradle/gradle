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

package org.gradle.plugin.repository;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.plugin.PluginId;

public interface RuleBasedPluginRepository extends PluginRepository {

    String getDescription();

    void setDescription(String description);

    void artifactRepositories(Action<RuleBasedArtifactRepositories> action);

    void artifactRepositories(Closure action);

    void pluginResolution(RubeBasedPluginResolution resolution);

    void pluginResolution(Closure resolution);

    interface RubeBasedPluginResolution {
        void findPlugin(PluginRequest plugin, PluginDependency target);
    }

    interface PluginDependency {

        /**
         * Adds a dependency to the plugin classpath.
         *
         * @param dependencyNotation resolvable by {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}
         *
         * @return a plugin option, to configure options about the dependency
         */
        PluginModuleOptions useModule(Object dependencyNotation);

        /**
         * Adds a dependency to the plugin classpath.
         *
         * @param dependencyNotation resolvable by {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object,Closure)}
         * @param configureClosure The closure to use to configure the dependency.
         *
         * @return a plugin option, to configure options about the dependency
         */
        PluginModuleOptions useModule(Object dependencyNotation, Closure configureClosure);
    }

    @HasInternalProtocol
    interface PluginModuleOptions {
        PluginModuleOptions withIsolatedClasspath();

        Object getDependenyNotation();
    }

    interface PluginRequest {
        PluginId getId();

        @Nullable
        String getVersion();
    }

    interface RuleBasedArtifactRepositories {

        MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action);

        IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action);
    }
}
