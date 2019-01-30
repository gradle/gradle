/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.MutableSoftwareComponentVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.OutgoingConfigurationUsageContext;
import org.gradle.internal.component.external.model.DefaultMutableCapabilities;

import java.util.List;
import java.util.Set;

public class DefaultAdhocSoftwareComponent implements AdhocComponentWithVariants, SoftwareComponentInternal {
    private final String componentName;
    private final ImmutableAttributesFactory attributesFactory;
    private final List<UsageContext> variants = Lists.newArrayListWithExpectedSize(4);

    public DefaultAdhocSoftwareComponent(String componentName, ImmutableAttributesFactory attributesFactory) {
        this.componentName = componentName;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public String getName() {
        return componentName;
    }

    @Override
    public void addVariantFromConfiguration(String name, Configuration outgoingConfiguration) {
        variants.add(new OutgoingConfigurationUsageContext(name, outgoingConfiguration));
    }

    @Override
    public void addVariantFromConfiguration(String name, Configuration outgoingConfiguration, Action<? super MutableSoftwareComponentVariant> configureAction) {
        variants.add(new LazyMutableSoftwareComponentVariant(
                new OutgoingConfigurationUsageContext(name, outgoingConfiguration), attributesFactory, configureAction
        ));
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        return ImmutableSet.copyOf(variants);
    }

    private final static class LazyMutableSoftwareComponentVariant implements MutableSoftwareComponentVariant, UsageContext {
        private final OutgoingConfigurationUsageContext delegate;
        private final ImmutableAttributesFactory attributesFactory;
        private final Action<? super MutableSoftwareComponentVariant> configureAction;
        private ImmutableAttributes attributes;
        private Set<? extends Capability> capabilities;

        private LazyMutableSoftwareComponentVariant(OutgoingConfigurationUsageContext delegate, ImmutableAttributesFactory attributesFactory, Action<? super MutableSoftwareComponentVariant> configureAction) {
            this.delegate = delegate;
            this.attributesFactory = attributesFactory;
            this.configureAction = configureAction;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public Set<? extends DependencyConstraint> getDependencyConstraints() {
            return delegate.getDependencyConstraints();
        }

        @Override
        public Set<? extends Capability> getCapabilities() {
            configure();
            return capabilities;
        }

        @Override
        public Set<ExcludeRule> getGlobalExcludes() {
            return delegate.getGlobalExcludes();
        }

        public Usage getUsage() {
            return delegate.getUsage();
        }

        @Override
        public AttributeContainer getAttributes() {
            configure();
            return attributes;
        }

        @Override
        public Set<PublishArtifact> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public AttributeContainer getSourceAttributes() {
            return ((AttributeContainerInternal) delegate.getAttributes()).asImmutable();
        }

        @Override
        public Set<? extends Capability> getSourceCapabilities() {
            return delegate.getCapabilities();
        }

        @Override
        public void attributes(Action<? super AttributeContainer> configuration) {
            AttributeContainerInternal mutable = attributesFactory.mutable();
            configuration.execute(mutable);
            this.attributes = mutable.asImmutable();
        }

        @Override
        public void capabilities(Action<? super MutableCapabilitiesMetadata> configuration) {
            DefaultMutableCapabilities mutable = new DefaultMutableCapabilities(Lists.<Capability>newArrayListWithExpectedSize(2));
            configuration.execute(mutable);
            this.capabilities = ImmutableSet.copyOf(mutable.getCapabilities());
        }

        private synchronized void configure() {
            if (attributes == null) {
                configureAction.execute(this);
            }
            if (attributes == null) {
                attributes = (ImmutableAttributes) getSourceAttributes();
            }
            if (capabilities == null) {
                capabilities = ImmutableSet.copyOf(getSourceCapabilities());
            }
        }
    }

}
