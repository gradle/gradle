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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.ConfigurationVariantMapping;
import org.gradle.api.internal.java.usagecontext.LazyConfigurationUsageContext;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 *
 *  @deprecated Replaced by the {@link org.gradle.api.component.AdhocComponentWithVariants public software component API}
 */
@Deprecated
public class JavaLibrary implements SoftwareComponentInternal {

    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private List<ConfigurationVariantMapping> featureVariants;

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

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public Set<UsageContext> getUsages() {
        if (featureVariants == null) {
            return ImmutableSet.of(runtimeUsage, compileUsage);
        }
        ImmutableSet.Builder<UsageContext> builder = ImmutableSet.builderWithExpectedSize(2 + featureVariants.size());
        builder.add(runtimeUsage);
        builder.add(compileUsage);
        for (ConfigurationVariantMapping mapping : featureVariants) {
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

}
