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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.IncompatibleConfigurationSelectionException;
import org.gradle.internal.exceptions.ConfigurationNotConsumableException;
import org.gradle.util.GUtil;

import java.util.List;

public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
    private final ComponentIdentifier componentId;
    private final ComponentSelector selector;
    private final String moduleConfiguration;
    private final String dependencyConfiguration;
    private final List<ExcludeMetadata> excludes;
    private final ImmutableList<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final boolean pending;

    private final AttributeContainer moduleAttributes;

    public LocalComponentDependencyMetadata(ComponentIdentifier componentId,
                                            ComponentSelector selector,
                                            String moduleConfiguration,
                                            AttributeContainer moduleAttributes,
                                            String dependencyConfiguration,
                                            List<IvyArtifactName> artifactNames, List<ExcludeMetadata> excludes,
                                            boolean force, boolean changing, boolean transitive, boolean pending) {
        this.componentId = componentId;
        this.selector = selector;
        this.moduleConfiguration = moduleConfiguration;
        this.moduleAttributes = moduleAttributes;
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = ImmutableList.copyOf(artifactNames);
        this.excludes = excludes;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
        this.pending = pending;
    }

    @Override
    public String toString() {
        return "dependency: " + selector + " from-conf: " + moduleConfiguration + " to-conf: " + dependencyConfiguration;
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

    private static String getOrDefaultConfiguration(String configuration) {
        return GUtil.elvis(configuration, Dependency.DEFAULT_CONFIGURATION);
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        boolean consumerHasAttributes = !consumerAttributes.isEmpty();
        List<? extends ConfigurationMetadata> consumableConfigurations = targetComponent.getVariantsForGraphTraversal();
        boolean useConfigurationAttributes = dependencyConfiguration == null && (consumerHasAttributes || !consumableConfigurations.isEmpty());
        if (useConfigurationAttributes) {
            return ImmutableList.of(AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(consumerAttributes, targetComponent, consumerSchema));
        }

        String targetConfiguration = GUtil.elvis(dependencyConfiguration, Dependency.DEFAULT_CONFIGURATION);
        ConfigurationMetadata toConfiguration = targetComponent.getConfiguration(targetConfiguration);
        if (toConfiguration == null) {
            throw new ConfigurationNotFoundException(componentId, moduleConfiguration, targetConfiguration, targetComponent.getComponentId());
        }
        if (!toConfiguration.isCanBeConsumed()) {
            throw new ConfigurationNotConsumableException(targetComponent.toString(), toConfiguration.getName());
        }
        if (consumerHasAttributes && !toConfiguration.getAttributes().isEmpty()) {
            // need to validate that the selected configuration still matches the consumer attributes
            AttributesSchemaInternal producerAttributeSchema = targetComponent.getAttributesSchema();
            if (!consumerSchema.withProducer(producerAttributeSchema).isMatching(toConfiguration.getAttributes(), consumerAttributes)) {
                throw new IncompatibleConfigurationSelectionException(consumerAttributes, consumerSchema.withProducer(producerAttributeSchema), targetComponent, targetConfiguration);
            }
        }
        return ImmutableList.of(toConfiguration);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
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
    public boolean isPending() {
        return pending;
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return artifactNames;
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        if (selector.equals(target)) {
            return this;
        }
        return copyWithTarget(target);
    }

    private LocalOriginDependencyMetadata copyWithTarget(ComponentSelector selector) {
        return new LocalComponentDependencyMetadata(componentId, selector, moduleConfiguration, moduleAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, pending);
    }
}
