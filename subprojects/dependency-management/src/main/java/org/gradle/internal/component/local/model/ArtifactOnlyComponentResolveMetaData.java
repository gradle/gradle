/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.Maps;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lightweight implementation of ComponentResolveMetaData that restricts how much state is retained for a local component between
 * resolving the component and resolving the artifacts.
 *
 * This is required because the artifact set is permitted to change during task execution, so we cannot simply resolve the set of
 * artifact ids early. This behaviour is deprecated, and once removed we will be able to simply hold onto Artifact ids to resolve later.
 */
class ArtifactOnlyComponentResolveMetaData implements ComponentResolveMetaData {
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private final String status;
    private final Map<String, ConfigurationMetaData> configurations = Maps.newHashMap();
    private final Map<String, PublishArtifactSet> publishArtifacts;

    ArtifactOnlyComponentResolveMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, String status,
                                         Map<String, PublishArtifactSet> publishArtifacts, Collection<? extends ConfigurationMetaData> configurations) {
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.status = status;
        this.publishArtifacts = publishArtifacts;
        for (ConfigurationMetaData configuration : configurations) {
            this.configurations.put(configuration.getName(), new ArtifactOnlyConfigurationMetaData(configuration.getName(), configuration.getHierarchy()));
        }
    }

    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Set<String> getConfigurationNames() {
        return configurations.keySet();
    }

    public ConfigurationMetaData getConfiguration(String name) {
        return configurations.get(name);
    }

    public List<DependencyMetaData> getDependencies() {
        throw new UnsupportedOperationException();
    }

    public ModuleSource getSource() {
        return null;
    }

    public ComponentResolveMetaData withSource(ModuleSource source) {
        throw new UnsupportedOperationException();
    }

    public boolean isGenerated() {
        return false;
    }

    public boolean isChanging() {
        return false;
    }

    public List<String> getStatusScheme() {
        return DEFAULT_STATUS_SCHEME;
    }

    private class ArtifactOnlyConfigurationMetaData implements ConfigurationMetaData {

        private final String name;
        private final Set<String> hierarchy;

        private ArtifactOnlyConfigurationMetaData(String name, Set<String> hierarchy) {
            this.name = name;
            this.hierarchy = hierarchy;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getHierarchy() {
            return hierarchy;
        }

        @Override
        public ComponentResolveMetaData getComponent() {
            return ArtifactOnlyComponentResolveMetaData.this;
        }

        @Override
        public Set<ComponentArtifactMetaData> getArtifacts() {
            return DefaultLocalComponentMetaData.getArtifacts(componentIdentifier, id, hierarchy, publishArtifacts);
        }

        @Override
        public boolean isTransitive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isVisible() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ComponentArtifactMetaData artifact(IvyArtifactName artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DependencyMetaData> getDependencies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<ExcludeRule> getExcludeRules() {
            throw new UnsupportedOperationException();
        }
    }
}
