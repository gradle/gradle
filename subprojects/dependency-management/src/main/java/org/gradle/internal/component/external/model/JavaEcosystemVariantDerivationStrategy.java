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
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

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
        String prefix = enforcedPlatform ? "enforced-platform-" : "platform-";
        DefaultConfigurationMetadata metadata = conf.withAttributes(prefix + conf.getName(), attributes);
        metadata = metadata.withConstraintsOnly();
        if (enforcedPlatform) {
            metadata = metadata.withForcedDependencies();
        }
        return metadata;
    }
}
