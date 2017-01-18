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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.component.AmbiguousConfigurationSelectionException;
import org.gradle.internal.component.NoMatchingConfigurationSelectionException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.exceptions.ConfigurationNotConsumableException;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
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
    private final AttributeContainer moduleAttributes;

    public LocalComponentDependencyMetadata(ComponentSelector selector, ModuleVersionSelector requested,
                                            String moduleConfiguration,
                                            AttributeContainer moduleAttributes,
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
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema) {
        // assert fromConfiguration.getHierarchy().contains(getOrDefaultConfiguration(moduleConfiguration));
        AttributeContainerInternal fromConfigurationAttributes = fromConfiguration.getAttributes();
        boolean consumerHasAttributes = !fromConfigurationAttributes.isEmpty();
        boolean useConfigurationAttributes = dependencyConfiguration == null && consumerHasAttributes;
        AttributesSchema producerAttributeSchema = targetComponent instanceof LocalComponentMetadata ? ((LocalComponentMetadata) targetComponent).getAttributesSchema() : attributesSchema;
        if (useConfigurationAttributes) {
            List<HasAttributes> consumableConfigurations = getConfigurationsAsHasAttributes(targetComponent);
            List<ConfigurationMetadata> matches = Cast.uncheckedCast(((AttributesSchemaInternal) attributesSchema).getMatches(producerAttributeSchema, consumableConfigurations, fromConfigurationAttributes));
            if (matches.size() == 1) {
                return ImmutableSet.of(ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(matches.get(0), fromConfigurationAttributes));
            } else if (!matches.isEmpty()) {
                throw new AmbiguousConfigurationSelectionException(fromConfigurationAttributes, attributesSchema, matches, targetComponent);
            }/* else {
                Set<String> configurationNames = Sets.newTreeSet();
                configurationNames.addAll(targetComponent.getConfigurationNames());
                throw new NoMatchingConfigurationSelectionException(fromConfigurationAttributes, targetComponent, configurationNames);
            }*/
        }

        String targetConfiguration = GUtil.elvis(dependencyConfiguration, Dependency.DEFAULT_CONFIGURATION);
        ConfigurationMetadata toConfiguration = targetComponent.getConfiguration(targetConfiguration);
        if (toConfiguration == null) {
            throw new ConfigurationNotFoundException(fromComponent.getComponentId(), moduleConfiguration, targetConfiguration, targetComponent.getComponentId());
        }
        if (!toConfiguration.isCanBeConsumed()) {
            if (dependencyConfiguration == null) {
                // this was a fallback to `default`, and `default` is not consumable
                Set<String> configurationNames = Sets.newTreeSet();
                configurationNames.addAll(targetComponent.getConfigurationNames());
                throw new NoMatchingConfigurationSelectionException(fromConfigurationAttributes, attributesSchema, targetComponent, Lists.newArrayList(configurationNames));
            }
            // explicit configuration selection
            throw new ConfigurationNotConsumableException(targetComponent.toString(), toConfiguration.getName());
        }
        ConfigurationMetadata delegate = toConfiguration;
        if (consumerHasAttributes) {
            if (!delegate.getAttributes().isEmpty()) {
                // need to validate that the selected configuration still matches the consumer attributes
                List<? extends HasAttributes> matches = ((AttributesSchemaInternal) attributesSchema).getMatches(producerAttributeSchema, Collections.singletonList((HasAttributes) delegate), fromConfigurationAttributes);
                if (matches.isEmpty()) {
                    throw new NoMatchingConfigurationSelectionException(fromConfigurationAttributes, attributesSchema, targetComponent, Collections.singletonList(targetConfiguration));
                }
            }
        }
        if (useConfigurationAttributes) {
            delegate = ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(delegate, fromConfigurationAttributes);
        }
        return ImmutableSet.of(delegate);
    }

    private List<HasAttributes> getConfigurationsAsHasAttributes(ComponentResolveMetadata targetComponent) {
        return Cast.uncheckedCast(targetComponent.getConsumableConfigurationsHavingAttributes());
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
        private final AttributeContainerInternal attributes;

        private static ConfigurationMetadata wrapIfLocal(ConfigurationMetadata md, AttributeContainerInternal attributes) {
            if (md instanceof LocalConfigurationMetadata) {
                return new ClientAttributesPreservingConfigurationMetadata((LocalConfigurationMetadata) md, attributes);
            }
            return md;
        }

        private ClientAttributesPreservingConfigurationMetadata(LocalConfigurationMetadata delegate, AttributeContainerInternal attributes) {
            this.delegate = delegate;
            this.attributes = attributes;
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return attributes;
        }

        @Override
        public boolean isCanBeConsumed() {
            return delegate.isCanBeConsumed();
        }

        @Override
        public boolean isCanBeResolved() {
            return delegate.isCanBeResolved();
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
        public List<? extends LocalOriginDependencyMetadata> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public Set<? extends LocalComponentArtifactMetadata> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public Set<? extends VariantMetadata> getVariants() {
            return delegate.getVariants();
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
        public Set<LocalFileDependencyMetadata> getFiles() {
            return delegate.getFiles();
        }
    }

}
