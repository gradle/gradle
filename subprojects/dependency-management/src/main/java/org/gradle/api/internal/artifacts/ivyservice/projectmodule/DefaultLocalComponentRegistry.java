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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultLocalComponentRegistry implements LocalComponentRegistry {
    private final List<LocalComponentProvider> providers;
    private final ConcurrentMap<ProjectComponentIdentifier, LocalComponentMetadata> projects = new ConcurrentHashMap<ProjectComponentIdentifier, LocalComponentMetadata>();

    public DefaultLocalComponentRegistry(List<LocalComponentProvider> providers) {
        this.providers = providers;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier projectIdentifier) {
        LocalComponentMetadata metaData = projects.get(projectIdentifier);
        if (metaData !=null) {
            return metaData;
        }
        for (LocalComponentProvider provider : providers) {
            LocalComponentMetadata componentMetaData = provider.getComponent(projectIdentifier);
            if (componentMetaData != null) {
                projects.putIfAbsent(projectIdentifier, componentMetaData);
                return componentMetaData;
            }
        }
        return null;
    }

    @Override
    public Iterable<LocalComponentArtifactMetadata> getAdditionalArtifacts(ProjectComponentIdentifier project) {
        for (LocalComponentProvider provider : providers) {
            Iterable<LocalComponentArtifactMetadata> artifacts = provider.getAdditionalArtifacts(project);
            if (artifacts != null) {
                return artifacts;
            }
        }
        return Collections.emptyList();
    }
}
