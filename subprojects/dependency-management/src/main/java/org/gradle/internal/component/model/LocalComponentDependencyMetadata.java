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

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.IncompatibleConfigurationSelectionException;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.exceptions.ConfigurationNotConsumableException;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
    private final ComponentIdentifier componentId;
    private final ComponentSelector selector;
    private final String moduleConfiguration;
    private final String dependencyConfiguration;
    private final List<ExcludeMetadata> excludes;
    private final List<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final boolean constraint;
    private final boolean endorsing;
    private final boolean fromLock;
    private final String reason;

    private final AttributeContainer moduleAttributes;
    private final ImmutableAttributes dependencyAttributes;

    public LocalComponentDependencyMetadata(ComponentIdentifier componentId,
                                            ComponentSelector selector,
                                            @Nullable String moduleConfiguration,
                                            @Nullable AttributeContainer moduleAttributes,
                                            AttributeContainer dependencyAttributes,
                                            @Nullable String dependencyConfiguration,
                                            List<IvyArtifactName> artifactNames,
                                            List<ExcludeMetadata> excludes,
                                            boolean force, boolean changing, boolean transitive, boolean constraint, boolean endorsing,
                                            @Nullable String reason) {
        this(componentId, selector, moduleConfiguration, moduleAttributes, dependencyAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, false, reason);
    }

    public LocalComponentDependencyMetadata(ComponentIdentifier componentId,
                                            ComponentSelector selector,
                                            @Nullable String moduleConfiguration,
                                            AttributeContainer moduleAttributes,
                                            AttributeContainer dependencyAttributes,
                                            @Nullable String dependencyConfiguration,
                                            List<IvyArtifactName> artifactNames,
                                            List<ExcludeMetadata> excludes,
                                            boolean force, boolean changing, boolean transitive,
                                            boolean constraint, boolean endorsing, boolean fromLock,
                                            @Nullable String reason) {
        this.componentId = componentId;
        this.selector = selector;
        this.moduleConfiguration = moduleConfiguration;
        this.moduleAttributes = moduleAttributes;
        this.dependencyAttributes = ((AttributeContainerInternal) dependencyAttributes).asImmutable();
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = asImmutable(artifactNames);
        this.excludes = excludes;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
        this.constraint = constraint;
        this.endorsing = endorsing;
        this.fromLock = fromLock;
        this.reason = reason;
    }

    private static List<IvyArtifactName> asImmutable(List<IvyArtifactName> artifactNames) {
        return artifactNames.isEmpty() ? Collections.emptyList() : artifactNames instanceof ImmutableList ? artifactNames : ImmutableList.copyOf(artifactNames);
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

    /**
     * Choose a single target configuration based on: a) the consumer attributes, b) the target configuration name and c) the target component
     *
     * Use attribute matching to choose a single variant when:
     * - The target configuration name is not specified AND
     * - Either: we have consumer attributes OR the target component has variants.
     *
     * Otherwise, revert to legacy selection of target configuration.
     *
     * @return A List containing a single `ConfigurationMetadata` representing the target variant.
     */
    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        boolean consumerHasAttributes = !consumerAttributes.isEmpty();
        Optional<ImmutableList<? extends ConfigurationMetadata>> targetVariants = targetComponent.getVariantsForGraphTraversal();
        boolean useConfigurationAttributes = dependencyConfiguration == null && (consumerHasAttributes || targetVariants.isPresent());
        if (useConfigurationAttributes) {
            return ImmutableList.of(AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(consumerAttributes, explicitRequestedCapabilities, targetComponent, consumerSchema, getArtifacts()));
        }

        String targetConfiguration = GUtil.elvis(dependencyConfiguration, Dependency.DEFAULT_CONFIGURATION);
        ConfigurationMetadata toConfiguration = targetComponent.getConfiguration(targetConfiguration);
        if (toConfiguration == null) {
            throw new ConfigurationNotFoundException(componentId, moduleConfiguration, targetConfiguration, targetComponent.getId());
        }
        verifyConsumability(targetComponent, toConfiguration);
        if (consumerHasAttributes && !toConfiguration.getAttributes().isEmpty()) {
            // need to validate that the selected configuration still matches the consumer attributes
            // Note that this validation only occurs when `dependencyConfiguration != null` (otherwise we would select with attribute matching)
            AttributesSchemaInternal producerAttributeSchema = targetComponent.getAttributesSchema();
            if (!consumerSchema.withProducer(producerAttributeSchema).isMatching(toConfiguration.getAttributes(), consumerAttributes)) {
                throw new IncompatibleConfigurationSelectionException(consumerAttributes, consumerSchema.withProducer(producerAttributeSchema), targetComponent, targetConfiguration, targetVariants.isPresent(), DescriberSelector.selectDescriber(consumerAttributes, consumerSchema));
            }
        }
        return ImmutableList.of(toConfiguration);
    }

    private void verifyConsumability(ComponentResolveMetadata targetComponent, ConfigurationMetadata toConfiguration) {
        if (!toConfiguration.isCanBeConsumed()) {
            throw new ConfigurationNotConsumableException(targetComponent.toString(), toConfiguration.getName());
        }
        DeprecationMessageBuilder.WithDocumentation consumptionDeprecation = toConfiguration.getConsumptionDeprecation();
        if (consumptionDeprecation != null) {
            consumptionDeprecation.nagUser();
        }
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
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return endorsing;
    }

    private boolean isExternalVariant() {
        return false;
    }

    @Override
    public String getReason() {
        return reason;
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

    @Override
    public LocalOriginDependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (selector.equals(target) && artifacts.equals(getArtifacts())) {
            return this;
        }
        return copyWithTargetAndArtifacts(target, artifacts);
    }

    @Override
    public LocalOriginDependencyMetadata forced() {
        if (force) {
            return this;
        }
        return copyWithForce();
    }

    @Override
    public boolean isFromLock() {
        return fromLock;
    }

    @Override
    public DependencyMetadata withReason(String reason) {
        if (Objects.equal(reason, this.reason)) {
            return this;
        }
        return copyWithReason(reason);
    }

    private LocalOriginDependencyMetadata copyWithTarget(ComponentSelector selector) {
        return new LocalComponentDependencyMetadata(componentId, selector, moduleConfiguration, moduleAttributes, dependencyAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithTargetAndArtifacts(ComponentSelector selector, List<IvyArtifactName> artifactNames) {
        return new LocalComponentDependencyMetadata(componentId, selector, moduleConfiguration, moduleAttributes, dependencyAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithReason(String reason) {
        return new LocalComponentDependencyMetadata(componentId, selector, moduleConfiguration, moduleAttributes, dependencyAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithForce() {
        return new LocalComponentDependencyMetadata(componentId, selector, moduleConfiguration, moduleAttributes, dependencyAttributes, dependencyConfiguration, artifactNames, excludes, true, changing, transitive, constraint, endorsing, fromLock, reason);
    }

}
