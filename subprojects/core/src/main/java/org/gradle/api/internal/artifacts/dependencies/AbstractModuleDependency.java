/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import com.google.common.base.Objects;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultExcludeRuleContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.ConfigureUtil.configureUsing;

public abstract class AbstractModuleDependency extends AbstractDependency implements ModuleDependency {
    private final static Logger LOG = Logging.getLogger(AbstractModuleDependency.class);

    private ImmutableAttributesFactory attributesFactory;
    private NotationParser<Object, Capability> capabilityNotationParser;
    private DefaultExcludeRuleContainer excludeRuleContainer = new DefaultExcludeRuleContainer();
    private Set<DependencyArtifact> artifacts = new LinkedHashSet<>();
    private ImmutableActionSet<ModuleDependency> onMutate = ImmutableActionSet.empty();
    private AttributeContainerInternal attributes;
    private ModuleDependencyCapabilitiesInternal moduleDependencyCapabilities;

    @Nullable
    private String configuration;
    private boolean transitive = true;
    private boolean endorsing;

    protected AbstractModuleDependency(@Nullable String configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public ModuleDependency setTransitive(boolean transitive) {
        validateMutation(this.transitive, transitive);
        this.transitive = transitive;
        return this;
    }

    @Override
    public String getTargetConfiguration() {
        return configuration;
    }

    @Override
    public void setTargetConfiguration(@Nullable String configuration) {
        validateMutation(this.configuration, configuration);
        validateNotVariantAware();
        if (!artifacts.isEmpty()) {
            throw new InvalidUserCodeException("Cannot set target configuration when artifacts have been specified");
        }
        this.configuration = configuration;
    }

    @Override
    public ModuleDependency exclude(Map<String, String> excludeProperties) {
        if (excludeRuleContainer.maybeAdd(excludeProperties)) {
            validateMutation();
        }
        return this;
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        return excludeRuleContainer.getRules();
    }

    private void setExcludeRuleContainer(DefaultExcludeRuleContainer excludeRuleContainer) {
        this.excludeRuleContainer = excludeRuleContainer;
    }

    @Override
    public Set<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Set<DependencyArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public AbstractModuleDependency addArtifact(DependencyArtifact artifact) {
        validateNotVariantAware();
        validateNoTargetConfiguration();
        artifacts.add(artifact);
        return this;
    }

    @Override
    public DependencyArtifact artifact(Closure configureClosure) {
        return artifact(configureUsing(configureClosure));
    }

    @Override
    public DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
        validateNotVariantAware();
        validateNoTargetConfiguration();
        DefaultDependencyArtifact artifact = new DefaultDependencyArtifact();
        configureAction.execute(artifact);
        artifact.validate();
        artifacts.add(artifact);
        return artifact;
    }

    protected void copyTo(AbstractModuleDependency target) {
        super.copyTo(target);
        target.setArtifacts(new LinkedHashSet<>(getArtifacts()));
        target.setExcludeRuleContainer(new DefaultExcludeRuleContainer(getExcludeRules()));
        target.setTransitive(isTransitive());
        if (attributes != null) {
            // We can only have attributes if we have the factory, then need to copy
            target.setAttributes(attributesFactory.mutable(attributes.asImmutable()));
        }
        target.setAttributesFactory(attributesFactory);
        target.setCapabilityNotationParser(capabilityNotationParser);
        if (moduleDependencyCapabilities != null) {
            target.moduleDependencyCapabilities = moduleDependencyCapabilities.copy();
        }
    }

    protected boolean isKeyEquals(ModuleDependency dependencyRhs) {
        if (getGroup() != null ? !getGroup().equals(dependencyRhs.getGroup()) : dependencyRhs.getGroup() != null) {
            return false;
        }
        if (!getName().equals(dependencyRhs.getName())) {
            return false;
        }
        if (getTargetConfiguration() != null ? !getTargetConfiguration().equals(dependencyRhs.getTargetConfiguration())
            : dependencyRhs.getTargetConfiguration()!=null) {
            return false;
        }
        if (getVersion() != null ? !getVersion().equals(dependencyRhs.getVersion())
                : dependencyRhs.getVersion() != null) {
            return false;
        }
        return true;
    }

    protected boolean isCommonContentEquals(ModuleDependency dependencyRhs) {
        if (!isKeyEquals(dependencyRhs)) {
            return false;
        }
        if (isTransitive() != dependencyRhs.isTransitive()) {
            return false;
        }
        if (!Objects.equal(getArtifacts(), dependencyRhs.getArtifacts())) {
            return false;
        }
        if (!Objects.equal(getExcludeRules(), dependencyRhs.getExcludeRules())) {
            return false;
        }
        if (!Objects.equal(getAttributes(), dependencyRhs.getAttributes())) {
            return false;
        }
        if (!Objects.equal(getRequestedCapabilities(), dependencyRhs.getRequestedCapabilities())) {
            return false;
        }
        return true;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes == null ? ImmutableAttributes.EMPTY : attributes.asImmutable();
    }

    @Override
    public AbstractModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
        validateMutation();
        validateNotLegacyConfigured();
        if (attributesFactory == null) {
            warnAboutInternalApiUse("attributes");
            return this;
        }
        if (attributes == null) {
            attributes = attributesFactory.mutable();
        }
        configureAction.execute(attributes);
        return this;
    }

    @Override
    public ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
        validateMutation();
        validateNotLegacyConfigured();
        if (capabilityNotationParser == null) {
            warnAboutInternalApiUse("capabilities");
            return this;
        }
        if (moduleDependencyCapabilities == null) {
            moduleDependencyCapabilities = new DefaultMutableModuleDependencyCapabilitiesHandler(capabilityNotationParser);
        }
        configureAction.execute(moduleDependencyCapabilities);
        return this;
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        if (moduleDependencyCapabilities == null) {
            return Collections.emptyList();
        }
        return moduleDependencyCapabilities.getRequestedCapabilities();
    }

    @Override
    public void endorseStrictVersions() {
        this.endorsing = true;
    }

    @Override
    public void doNotEndorseStrictVersions() {
        this.endorsing = false;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return this.endorsing;
    }

    private void warnAboutInternalApiUse(String thing) {
        LOG.warn("Cannot set " + thing + " for dependency \"" + this.getGroup() + ":" + this.getName() + ":" + this.getVersion() + "\": it was probably created by a plugin using internal APIs");
    }

    public void setAttributesFactory(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    public void setCapabilityNotationParser(NotationParser<Object, Capability> capabilityNotationParser) {
        this.capabilityNotationParser = capabilityNotationParser;
    }

    protected ImmutableAttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    private void setAttributes(AttributeContainerInternal attributes) {
        this.attributes = attributes;
    }

    @SuppressWarnings("unchecked")
    public void addMutationValidator(Action<? super ModuleDependency> action) {
        this.onMutate = onMutate.add(action);
    }

    protected void validateMutation() {
        onMutate.execute(this);
    }

    protected void validateMutation(Object currentValue, Object newValue) {
        if (!Objects.equal(currentValue, newValue)) {
            validateMutation();
        }
    }

    private void validateNotVariantAware() {
        if (!getAttributes().isEmpty() || !getRequestedCapabilities().isEmpty()) {
            throw new InvalidUserCodeException("Cannot set artifact / configuration information on a dependency that has attributes or capabilities configured");
        }
    }

    private void validateNotLegacyConfigured() {
        if (getTargetConfiguration() != null || !getArtifacts().isEmpty()) {
            throw new InvalidUserCodeException("Cannot add attributes or capabilities on a dependency that specifies artifacts or configuration information");
        }
    }

    private void validateNoTargetConfiguration() {
        if (configuration != null) {
            throw new InvalidUserCodeException("Cannot add artifact if target configuration has been set");
        }
    }

}
