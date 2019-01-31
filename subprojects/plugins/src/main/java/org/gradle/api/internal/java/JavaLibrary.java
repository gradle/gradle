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

package org.gradle.api.internal.java;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentWithFeatures;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.FeatureConfigurationUsageContext;
import org.gradle.api.internal.java.usagecontext.LazyConfigurationUsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;

import javax.inject.Inject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements ComponentWithFeatures, SoftwareComponentInternal {

    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private List<FeatureMapping> featureVariants;

    @Inject
    public JavaLibrary(ObjectFactory objectFactory, ConfigurationContainer configurations, ImmutableAttributesFactory attributesFactory, PublishArtifact artifact) {
        this.configurations = configurations;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.runtimeUsage = createRuntimeUsageContext();
        this.compileUsage = createCompileUsageContext();
        if (artifact != null) {
            this.artifacts.add(artifact);
        }
    }

    @VisibleForTesting
    Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public String getName() {
        return "java";
    }

    public Set<UsageContext> getUsages() {
        if (featureVariants == null) {
            return ImmutableSet.of(runtimeUsage, compileUsage);
        }
        ImmutableSet.Builder<UsageContext> builder = ImmutableSet.builderWithExpectedSize(2 + featureVariants.size());
        builder.add(runtimeUsage);
        builder.add(compileUsage);
        for (FeatureMapping mapping : featureVariants) {
            mapping.validate();
            mapping.collectUsageContexts(builder);
        }
        return builder.build();
    }

    private UsageContext createRuntimeUsageContext() {
        ImmutableAttributes attributes = attributesFactory.of(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        return new LazyConfigurationUsageContext("runtime", RUNTIME_ELEMENTS_CONFIGURATION_NAME, artifacts, configurations, attributes);
    }

    private UsageContext createCompileUsageContext() {
        ImmutableAttributes attributes = attributesFactory.of(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        return new LazyConfigurationUsageContext("api", API_ELEMENTS_CONFIGURATION_NAME, artifacts, configurations, attributes);
    }

    @Override
    public void addFeatureVariantsFromConfiguration(Configuration outgoingConfiguration, Spec<? super ConfigurationVariant> spec) {
        if (featureVariants == null) {
            featureVariants = Lists.newArrayListWithExpectedSize(2);
        }
        featureVariants.add(new FeatureMapping(outgoingConfiguration, spec));
    }


    private static class FeatureMapping {
        private final Configuration outgoingConfiguration;
        private final Spec<? super ConfigurationVariant> spec;

        private FeatureMapping(Configuration outgoingConfiguration, Spec<? super ConfigurationVariant> spec) {
            this.outgoingConfiguration = outgoingConfiguration;
            this.spec = spec;
        }

        private void assertNoDuplicateVariant(String name, Set<String> seen) {
            if ("runtime".equals(name) || "api".equals(name) || !seen.add(name)) {
                throw new InvalidUserDataException("Cannot add feature variant '" + name + "' as a variant with the same name is already registered");
            }
        }

        void collectUsageContexts(final ImmutableSet.Builder<UsageContext> outgoing) {
            Set<String> seen = Sets.newHashSet();
            DefaultConfigurationVariant defaultConfigurationVariant = new DefaultConfigurationVariant();
            if (spec.isSatisfiedBy(defaultConfigurationVariant)) {
                assertNoDuplicateVariant(outgoingConfiguration.getName(), seen);
                outgoing.add(new FeatureConfigurationUsageContext(outgoingConfiguration.getName(), outgoingConfiguration, defaultConfigurationVariant));
            }
            NamedDomainObjectContainer<ConfigurationVariant> extraVariants = outgoingConfiguration.getOutgoing().getVariants();
            for (ConfigurationVariant variant : extraVariants) {
                if (spec.isSatisfiedBy(variant)) {
                    String name = outgoingConfiguration.getName() + StringUtils.capitalize(variant.getName());
                    assertNoDuplicateVariant(name, seen);
                    outgoing.add(new FeatureConfigurationUsageContext(
                            name,
                            outgoingConfiguration,
                            variant
                    ));
                }
            }
        }

        public void validate() {
            Collection<? extends Capability> capabilities = outgoingConfiguration.getOutgoing().getCapabilities();
            if (capabilities.isEmpty()) {
                throw new InvalidUserDataException("Cannot publish feature variant " + outgoingConfiguration.getName() + " because configuration " + outgoingConfiguration.getName() + " doesn't declare any capability");
            }
        }

        private class DefaultConfigurationVariant implements ConfigurationVariant {
            @Override
            public PublishArtifactSet getArtifacts() {
                return outgoingConfiguration.getArtifacts();
            }

            @Override
            public void artifact(Object notation) {
                throw new InvalidUserCodeException("Cannot add artifacts during filtering");
            }

            @Override
            public void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
                throw new InvalidUserCodeException("Cannot add artifacts during filtering");
            }

            @Override
            public String getName() {
                return outgoingConfiguration.getName();
            }

            @Override
            public ConfigurationVariant attributes(Action<? super AttributeContainer> action) {
                throw new InvalidUserCodeException("Cannot mutate outgoing configuration during filtering");
            }

            @Override
            public AttributeContainer getAttributes() {
                return outgoingConfiguration.getAttributes();
            }
        }
    }
}
