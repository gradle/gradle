/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.Collections;

public class JavaEcosystemVariantDerivationStrategy implements VariantDerivationStrategy {
    @Override
    public boolean derivesVariants() {
        return true;
    }

    @Override
    public ImmutableList<? extends ConfigurationMetadata> derive(ModuleComponentResolveMetadata metadata) {
        if (metadata instanceof DefaultMavenModuleResolveMetadata) {
            DefaultMavenModuleResolveMetadata md = (DefaultMavenModuleResolveMetadata) metadata;
            ImmutableAttributes attributes = md.getAttributes();
            MavenImmutableAttributesFactory attributesFactory = (MavenImmutableAttributesFactory) md.getAttributesFactory();
            DefaultConfigurationMetadata compileConfiguration = (DefaultConfigurationMetadata) md.getConfiguration("compile");
            DefaultConfigurationMetadata runtimeConfiguration = (DefaultConfigurationMetadata) md.getConfiguration("runtime");
            return ImmutableList.of(
                    // When deriving variants for the Java ecosystem, we actually have 2 components "mixed together": the library and the platform
                    // and there's no way to figure out what was the intent when it was published. So we derive variants, but we also need
                    // to use generic JAVA_API and JAVA_RUNTIME attributes, instead of more precise JAVA_API_JARS and JAVA_RUNTIME_JARS
                    // because of the platform aspect (which aren't jars but "something"). Using JAVA_API_JARS for the library part and
                    // JAVA_API for the platform would lead to selection of the platform when we don't want them (in other words in a single
                    // component we cannot mix precise usages with more generic ones)
                libraryWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API),
                libraryWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME),
                platformWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API, false),
                platformWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME, false),
                platformWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API, true),
                platformWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME, true));
        }
        return null;
    }

    private static ConfigurationMetadata libraryWithUsageAttribute(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, String usage) {
        ImmutableAttributes attributes = attributesFactory.libraryWithUsage(originAttributes, usage);
        return conf.withAttributes(attributes).withoutConstraints();
    }

    private static ConfigurationMetadata platformWithUsageAttribute(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, String usage, boolean enforcedPlatform) {
        ImmutableAttributes attributes = attributesFactory.platformWithUsage(originAttributes, usage, enforcedPlatform);
        ModuleComponentIdentifier componentId = conf.getComponentId();
        String prefix = enforcedPlatform ? "enforced-platform-" : "platform-";
        DefaultConfigurationMetadata metadata = conf.withAttributes(prefix + conf.getName(), attributes);
        ImmutableCapability shadowed = new ImmutableCapability(
                componentId.getGroup(),
                componentId.getModule(),
                componentId.getVersion()
        );
        metadata = metadata
                .withConstraintsOnly()
                .withCapabilities(Collections.singletonList(new DefaultShadowedCapability(shadowed, "-derived-platform")));
        if (enforcedPlatform) {
            metadata = metadata.withForcedDependencies();
        }
        return metadata;
    }

}
