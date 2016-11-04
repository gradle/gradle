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

package org.gradle.internal.component.model;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.configurations.ConfigurationAttributeMatchingStrategies;
import org.gradle.api.internal.artifacts.configurations.ConfigurationAttributesMatchingStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
    private static final Function<ConfigurationMetadata, String> CONFIG_NAME = new Function<ConfigurationMetadata, String>() {
        @Override
        public String apply(ConfigurationMetadata input) {
            return input.getName();
        }
    };

    private final ComponentSelector selector;
    private final ModuleVersionSelector requested;
    private final String moduleConfiguration;
    private final String dependencyConfiguration;
    private final List<Exclude> excludes;
    private final Set<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final ModuleExclusion exclusions;
    private final Map<String, String> moduleAttributes;

    public LocalComponentDependencyMetadata(ComponentSelector selector, ModuleVersionSelector requested,
                                            String moduleConfiguration,
                                            Map<String, String> moduleAttributes,
                                            String dependencyConfiguration,
                                            Set<IvyArtifactName> artifactNames, List<Exclude> excludes,
                                            boolean force, boolean changing, boolean transitive) {
        this.selector = selector;
        this.requested = requested;
        this.moduleConfiguration = moduleConfiguration;
        this.moduleAttributes = moduleAttributes;
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = artifactNames;
        this.excludes = excludes;
        this.exclusions = ModuleExclusions.excludeAny(excludes);
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
    }

    @Override
    public String toString() {
        return "dependency: " + requested + " from-conf: " + moduleConfiguration + " to-conf: " + dependencyConfiguration;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public ComponentSelector getSelector() {
        return selector;
    }

    @Override
    public String getModuleConfiguration() {
        return moduleConfiguration;
    }

    @Override
    public String getDependencyConfiguration() {
        return getOrDefaultConfiguration(dependencyConfiguration);
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, ConfigurationAttributesMatchingStrategyInternal attributesMatchingStrategy) {
        assert fromConfiguration.getHierarchy().contains(getOrDefaultConfiguration(moduleConfiguration));
        Map<String, String> attributes = fromConfiguration.getAttributes();
        boolean useConfigurationAttributes = dependencyConfiguration == null && !attributes.isEmpty();
        if (useConfigurationAttributes) {
            Set<String> configurationNames = targetComponent.getConfigurationNames();
            Map<ConfigurationMetadata, Map<String, String>> candidateConfigurations = Maps.newHashMap();
            for (String configurationName : configurationNames) {
                ConfigurationMetadata dependencyConfiguration = targetComponent.getConfiguration(configurationName);
                Map<String, String> dependencyConfigurationAttributes = dependencyConfiguration.getAttributes();
                if (!dependencyConfigurationAttributes.isEmpty() && dependencyConfiguration.isConsumeOrPublishAllowed()) {
                    candidateConfigurations.put(dependencyConfiguration, dependencyConfigurationAttributes);
                }
            }
            List<ConfigurationMetadata> bestMatches = ConfigurationAttributeMatchingStrategies.findBestMatches(attributesMatchingStrategy, attributes, candidateConfigurations);
            if (bestMatches.size()==1) {
                return ImmutableSet.of(ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(bestMatches.get(0), attributes));
            } else if (!bestMatches.isEmpty()) {
                throw new IllegalArgumentException("Cannot choose between the following configurations: " + Sets.newTreeSet(Lists.transform(bestMatches, CONFIG_NAME)) + ". All of them match the client attributes " + new TreeMap<String, String>(attributes));
            }
        }
        String targetConfiguration = GUtil.elvis(dependencyConfiguration, Dependency.DEFAULT_CONFIGURATION);
        ConfigurationMetadata toConfiguration = targetComponent.getConfiguration(targetConfiguration);
        if (toConfiguration == null) {
            throw new ConfigurationNotFoundException(fromComponent.getComponentId(), moduleConfiguration, targetConfiguration, targetComponent.getComponentId());
        }
        if (dependencyConfiguration!=null && !toConfiguration.isConsumeOrPublishAllowed()) {
            throw new IllegalArgumentException("Configuration '" + dependencyConfiguration + "' cannot be used in a project dependency");
        }
        ConfigurationMetadata delegate = toConfiguration;
        if (useConfigurationAttributes) {
            delegate = ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(delegate, attributes);
        }
        return ImmutableSet.of(delegate);
    }

    private static String getOrDefaultConfiguration(String configuration) {
        return GUtil.elvis(configuration, Dependency.DEFAULT_CONFIGURATION);
    }
    @Override
    public Set<String> getModuleConfigurations() {
        return ImmutableSet.of(getOrDefaultConfiguration(moduleConfiguration));
    }

    @Override
    public ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration) {
        assert fromConfiguration.getHierarchy().contains(getOrDefaultConfiguration(moduleConfiguration));
        return exclusions;
    }

    @Override
    public List<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public String getDynamicConstraintVersion() {
        return requested.getVersion();
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        if (artifactNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ComponentArtifactMetadata> artifacts = new LinkedHashSet<ComponentArtifactMetadata>();
        for (IvyArtifactName artifactName : artifactNames) {
            artifacts.add(toConfiguration.artifact(artifactName));
        }
        return artifacts;
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return artifactNames;
    }

    @Override
    public LocalOriginDependencyMetadata withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        ComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(newRequested);
        return copyWithTarget(newSelector, newRequested);
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleVersionSelector requestedVersion = DefaultModuleVersionSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersion());
            if (selector.equals(target) && requested.equals(requestedVersion)) {
                return this;
            }
            return copyWithTarget(moduleTarget, requestedVersion);
        } else if (target instanceof ProjectComponentSelector) {
            if (target.equals(selector)) {
                return this;
            }
            return copyWithTarget(target, requested);
        } else {
            throw new AssertionError("Invalid component selector type for substitution: " + target);
        }
    }

    private LocalOriginDependencyMetadata copyWithTarget(ComponentSelector selector, ModuleVersionSelector requested) {
        return new LocalComponentDependencyMetadata(selector, requested, moduleConfiguration, moduleAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive);
    }

    private static class ClientAttributesPreservingConfigurationMetadata implements LocalConfigurationMetadata {
        private final LocalConfigurationMetadata delegate;
        private final Map<String, String> attributes;

        private static ConfigurationMetadata wrapIfLocal(ConfigurationMetadata md, Map<String, String> attributes) {
            if (md instanceof LocalConfigurationMetadata) {
                return new ClientAttributesPreservingConfigurationMetadata((LocalConfigurationMetadata) md, attributes);
            }
            return md;
        }

        private ClientAttributesPreservingConfigurationMetadata(LocalConfigurationMetadata delegate, Map<String, String> attributes) {
            this.delegate = delegate;
            this.attributes = attributes;
        }

        @Override
        public Map<String, String> getAttributes() {
            return attributes;
        }

        @Override
        public boolean isConsumeOrPublishAllowed() {
            return delegate.isConsumeOrPublishAllowed();
        }

        @Override
        public boolean isQueryOrResolveAllowed() {
            return delegate.isQueryOrResolveAllowed();
        }

        @Override
        public Set<String> getHierarchy() {
            return delegate.getHierarchy();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public List<DependencyMetadata> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public Set<ComponentArtifactMetadata> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public ModuleExclusion getExclusions() {
            return delegate.getExclusions();
        }

        @Override
        public boolean isTransitive() {
            return delegate.isTransitive();
        }

        @Override
        public boolean isVisible() {
            return delegate.isVisible();
        }

        @Override
        public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
            return delegate.artifact(artifact);
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public Set<String> getExtendsFrom() {
            return delegate.getExtendsFrom();
        }

        @Override
        public TaskDependency getArtifactBuildDependencies() {
            return delegate.getArtifactBuildDependencies();
        }

        @Override
        public Set<LocalFileDependencyMetadata> getFiles() {
            return delegate.getFiles();
        }
    }
}
