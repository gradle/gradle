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

public class JavaEcosystemVariantDerivationStrategy extends AbstractStatelessDerivationStrategy {
    private static final JavaEcosystemVariantDerivationStrategy INSTANCE = new JavaEcosystemVariantDerivationStrategy();

    private JavaEcosystemVariantDerivationStrategy() {
    }

    public static JavaEcosystemVariantDerivationStrategy getInstance() {
        return INSTANCE;
    }

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
            ModuleComponentIdentifier componentId = md.getId();
            ImmutableCapabilities shadowedPlatformCapability = buildShadowPlatformCapability(componentId, false);
            ImmutableCapabilities shadowedEnforcedPlatformCapability = buildShadowPlatformCapability(componentId, true);
            return ImmutableList.of(
                    // When deriving variants for the Java ecosystem, we actually have 2 components "mixed together": the library and the platform
                    // and there's no way to figure out what was the intent when it was published. So we derive variants, but we also need
                    // to use generic JAVA_API and JAVA_RUNTIME attributes, instead of more precise JAVA_API_JARS and JAVA_RUNTIME_JARS
                    // because of the platform aspect (which aren't jars but "something"). Using JAVA_API_JARS for the library part and
                    // JAVA_API for the platform would lead to selection of the platform when we don't want them (in other words in a single
                    // component we cannot mix precise usages with more generic ones)
                libraryWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API),
                libraryWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME),
                libraryWithSourcesVariant(runtimeConfiguration, attributes, attributesFactory, metadata),
                libraryWithJavadocVariant(runtimeConfiguration, attributes, attributesFactory, metadata),
                platformWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API, false, shadowedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME, false, shadowedPlatformCapability),
                platformWithUsageAttribute(compileConfiguration, attributes, attributesFactory, Usage.JAVA_API, true, shadowedEnforcedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, attributesFactory, Usage.JAVA_RUNTIME, true, shadowedEnforcedPlatformCapability));
        }
        return null;
    }

    /**
     * Synthesizes a "sources" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the sources-classifier jar
     */
    private static DefaultConfigurationMetadata libraryWithSourcesVariant(DefaultConfigurationMetadata runtimeConfiguration, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, ModuleComponentResolveMetadata metadata) {
        return runtimeConfiguration.mutate()
            .withName("sources")
            .withAttributes(attributesFactory.sourcesVariant(originAttributes))
            .withArtifacts(ImmutableList.of(metadata.optionalArtifact("source", "jar", "sources")))
            .withoutConstraints()
            .build();
    }

    /**
     * Synthesizes a "javadoc" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the javadoc-classifier jar
     */
    private static DefaultConfigurationMetadata libraryWithJavadocVariant(DefaultConfigurationMetadata runtimeConfiguration, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, ModuleComponentResolveMetadata metadata) {
        return runtimeConfiguration.mutate()
            .withName("javadoc")
            .withAttributes(attributesFactory.javadocVariant(originAttributes))
            .withArtifacts(ImmutableList.of(metadata.optionalArtifact("javadoc", "jar", "javadoc")))
            .withoutConstraints()
            .build();
    }

    private ImmutableCapabilities buildShadowPlatformCapability(ModuleComponentIdentifier componentId, boolean enforced) {
        return ImmutableCapabilities.of(Collections.singletonList(
                new DefaultShadowedCapability(new ImmutableCapability(
                        componentId.getGroup(),
                        componentId.getModule(),
                        componentId.getVersion()
                ), enforced ? "-derived-enforced-platform" : "-derived-platform")
            )
        );
    }

    private static ConfigurationMetadata libraryWithUsageAttribute(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, String usage) {
        ImmutableAttributes attributes = attributesFactory.libraryWithUsage(originAttributes, usage);
        return conf.mutate()
                .withAttributes(attributes)
                .withoutConstraints()
                .build();
    }

    private static ConfigurationMetadata platformWithUsageAttribute(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes, MavenImmutableAttributesFactory attributesFactory, String usage, boolean enforcedPlatform, ImmutableCapabilities shadowedPlatformCapability) {
        ImmutableAttributes attributes = attributesFactory.platformWithUsage(originAttributes, usage, enforcedPlatform);
        String prefix = enforcedPlatform ? "enforced-platform-" : "platform-";
        DefaultConfigurationMetadata.Builder builder = conf.mutate()
                .withName(prefix + conf.getName())
                .withAttributes(attributes)
                .withConstraintsOnly()
                .withCapabilities(shadowedPlatformCapability);
        if (enforcedPlatform) {
            builder = builder.withForcedDependencies();
        }
        return builder.build();
    }

}
