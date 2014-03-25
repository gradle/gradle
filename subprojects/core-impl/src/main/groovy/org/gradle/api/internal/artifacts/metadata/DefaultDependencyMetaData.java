/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentSelector;
import org.gradle.api.internal.artifacts.component.DefaultProjectComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ReflectiveDependencyDescriptorFactory;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencyMetaData implements DependencyMetaData {
    private final DependencyDescriptor dependencyDescriptor;
    private final DefaultModuleVersionSelector requested;

    public DefaultDependencyMetaData(DependencyDescriptor dependencyDescriptor) {
        this.dependencyDescriptor = dependencyDescriptor;
        ModuleRevisionId dependencyRevisionId = dependencyDescriptor.getDependencyRevisionId();
        requested = new DefaultModuleVersionSelector(dependencyRevisionId.getOrganisation(), dependencyRevisionId.getName(), dependencyRevisionId.getRevision());
    }

    @Override
    public String toString() {
        return dependencyDescriptor.toString();
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public boolean isChanging() {
        return dependencyDescriptor.isChanging();
    }

    public boolean isTransitive() {
        return dependencyDescriptor.isTransitive();
    }

    public DependencyDescriptor getDescriptor() {
        return dependencyDescriptor;
    }

    public Set<ComponentArtifactMetaData> getArtifacts(ConfigurationMetaData fromConfiguration, ConfigurationMetaData toConfiguration) {
        String[] targetConfigurations = fromConfiguration.getHierarchy().toArray(new String[fromConfiguration.getHierarchy().size()]);
        DependencyArtifactDescriptor[] dependencyArtifacts = dependencyDescriptor.getDependencyArtifacts(targetConfigurations);
        if (dependencyArtifacts.length == 0) {
            return Collections.emptySet();
        }
        Set<ComponentArtifactMetaData> artifacts = new LinkedHashSet<ComponentArtifactMetaData>();
        for (DependencyArtifactDescriptor artifactDescriptor : dependencyArtifacts) {
            ModuleRevisionId id = toConfiguration.getComponent().getDescriptor().getModuleRevisionId();
            Artifact artifact = new DefaultArtifact(id, null, artifactDescriptor.getName(), artifactDescriptor.getType(), artifactDescriptor.getExt(), artifactDescriptor.getUrl(), artifactDescriptor.getQualifiedExtraAttributes());
            artifacts.add(toConfiguration.getComponent().artifact(artifact));
        }
        return artifacts;
    }

    public DependencyMetaData withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        return new DefaultDependencyMetaData(dependencyDescriptor.clone(IvyUtil.createModuleRevisionId(dependencyDescriptor.getDependencyRevisionId(), requestedVersion)));
    }

    public DependencyMetaData withRequestedVersion(ModuleVersionSelector requestedVersion) {
        if (requestedVersion.equals(requested)) {
            return this;
        }

        ModuleRevisionId requestedId = IvyUtil.createModuleRevisionId(requestedVersion.getGroup(), requestedVersion.getName(), requestedVersion.getVersion());
        DependencyDescriptor substitutedDescriptor = new ReflectiveDependencyDescriptorFactory().create(dependencyDescriptor, requestedId);
        return new DefaultDependencyMetaData(substitutedDescriptor);
    }

    public DependencyMetaData withChanging() {
        if (dependencyDescriptor.isChanging()) {
            return this;
        }

        DependencyDescriptor forcedChanging = dependencyDescriptor.clone(dependencyDescriptor.getDependencyRevisionId());
        try {
            Field field = DefaultDependencyDescriptor.class.getDeclaredField("isChanging");
            field.setAccessible(true);
            field.set(forcedChanging, true);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return new DefaultDependencyMetaData(forcedChanging);
    }

    public ComponentSelector getSelector() {
        if(dependencyDescriptor instanceof ProjectDependencyDescriptor) {
            return new DefaultProjectComponentSelector(((ProjectDependencyDescriptor)dependencyDescriptor).getTargetProject().getPath());
        }

        return DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), requested.getVersion());
    }
}
