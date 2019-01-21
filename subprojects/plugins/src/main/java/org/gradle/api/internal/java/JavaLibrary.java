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
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentWithOptionalFeatures;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.OptionalFeatureonfigurationUsageContext;
import org.gradle.api.internal.java.usagecontext.LazyConfigurationUsageContext;
import org.gradle.api.model.ObjectFactory;

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
public class JavaLibrary implements ComponentWithOptionalFeatures, SoftwareComponentInternal {

    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private List<OptionalFeatureMapping> optionalFeatures;

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
        if (optionalFeatures == null) {
            return ImmutableSet.of(runtimeUsage, compileUsage);
        }
        ImmutableSet.Builder<UsageContext> builder = ImmutableSet.builderWithExpectedSize(2 + optionalFeatures.size());
        builder.add(runtimeUsage);
        builder.add(compileUsage);
        for (OptionalFeatureMapping mapping : optionalFeatures) {
            mapping.validate();
            builder.add(mapping.toUsageContext());
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
    public void addOptionalFeatureVariantFromConfiguration(String name, Configuration outgoingConfiguration) {
        if (optionalFeatures == null) {
            optionalFeatures = Lists.newArrayListWithExpectedSize(2);
        }
        assertNoDuplicateVariant(name);
        optionalFeatures.add(new OptionalFeatureMapping(name, outgoingConfiguration));
    }

    private void assertNoDuplicateVariant(String name) {
        if ("runtime".equals(name) || "api".equals(name) ||
                Lists.transform(optionalFeatures, OptionalFeatureMapping.VARIANT_NAME).contains(name)) {
            throw new InvalidUserDataException("Cannot add optional feature variant '" + name + "' as a variant with the same name is already registered");
        }
    }

    private static class OptionalFeatureMapping {
        private static final Function<OptionalFeatureMapping, String> VARIANT_NAME = new Function<OptionalFeatureMapping, String>() {
            @Override
            public String apply(OptionalFeatureMapping input) {
                return input.variantName;
            }
        };

        private final String variantName;
        private final Configuration outgoingConfiguration;

        private OptionalFeatureMapping(String variantName, Configuration outgoingConfiguration) {
            this.variantName = variantName;
            this.outgoingConfiguration = outgoingConfiguration;
        }

        UsageContext toUsageContext() {
            return new OptionalFeatureonfigurationUsageContext(
                    variantName,
                    outgoingConfiguration
            );
        }

        public void validate() {
            Collection<? extends Capability> capabilities = outgoingConfiguration.getOutgoing().getCapabilities();
            if (capabilities.isEmpty()) {
                throw new InvalidUserDataException("Cannot publish optional feature variant " + variantName + " because configuration " + outgoingConfiguration.getName() + " doesn't declare any capability");
            }
        }
    }
}
