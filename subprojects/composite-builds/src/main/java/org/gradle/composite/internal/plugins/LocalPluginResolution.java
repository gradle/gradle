/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.composite.internal.plugins;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.local.PluginPublication;

import java.util.Optional;

class LocalPluginResolution implements PluginResolution {
    private final PluginId pluginId;
    private final ProjectInternal producingProject;

    private LocalPluginResolution(PluginId pluginId, ProjectInternal producingProject) {
        this.pluginId = pluginId;
        this.producingProject = producingProject;
    }

    @Override
    public PluginId getPluginId() {
        return pluginId;
    }

    @Override
    public String getPluginVersion() {
        return producingProject.getVersion().toString();
    }

    @Override
    public void execute(PluginResolveContext context) {
        context.addLegacy(pluginId, producingProject.getDependencies().create(producingProject));
    }

    static Optional<PluginResolution> resolvePlugin(GradleInternal gradle, PluginId requestedPluginId) {
        ProjectPublicationRegistry publicationRegistry = gradle.getServices().get(ProjectPublicationRegistry.class);
        for (ProjectPublicationRegistry.Reference<PluginPublication> reference : publicationRegistry.getPublications(PluginPublication.class)) {
            PluginId pluginId = reference.get().getPluginId();
            if (pluginId.equals(requestedPluginId)) {
                return Optional.of(new LocalPluginResolution(pluginId, reference.getProducingProject()));
            }
        }
        return Optional.empty();
    }
}
