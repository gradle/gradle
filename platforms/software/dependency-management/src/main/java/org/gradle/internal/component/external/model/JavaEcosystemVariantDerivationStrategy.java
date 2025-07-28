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
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;

import javax.inject.Inject;

public class JavaEcosystemVariantDerivationStrategy extends AbstractStatelessDerivationStrategy {

    private final MavenVariantAttributesFactory mavenAttributesFactory;

    @Inject
    public JavaEcosystemVariantDerivationStrategy(MavenVariantAttributesFactory mavenAttributesFactory) {
        this.mavenAttributesFactory = mavenAttributesFactory;
    }

    @Override
    public boolean derivesVariants() {
        return true;
    }

    @Override
    public ImmutableList<? extends ModuleConfigurationMetadata> derive(ModuleComponentResolveMetadata metadata) {
        if (metadata instanceof DefaultMavenModuleResolveMetadata) {
            DefaultMavenModuleResolveMetadata md = (DefaultMavenModuleResolveMetadata) metadata;
            ImmutableAttributes attributes = md.getAttributes();
            DefaultConfigurationMetadata compileConfiguration = (DefaultConfigurationMetadata) md.getConfiguration("compile");
            DefaultConfigurationMetadata runtimeConfiguration = (DefaultConfigurationMetadata) md.getConfiguration("runtime");
            ModuleComponentIdentifier componentId = md.getId();
            ImmutableCapabilities shadowedPlatformCapability = buildShadowPlatformCapability(componentId, false);
            ImmutableCapabilities shadowedEnforcedPlatformCapability = buildShadowPlatformCapability(componentId, true);
            return ImmutableList.of(
                // When deriving variants for the Java ecosystem, we actually have 2 components "mixed together": the library and the platform
                // and there's no way to figure out what was the intent when it was published. So we derive variants for both.
                libraryCompileScope(compileConfiguration, attributes),
                libraryRuntimeScope(runtimeConfiguration, attributes),
                libraryWithSourcesVariant(runtimeConfiguration, attributes, metadata),
                libraryWithJavadocVariant(runtimeConfiguration, attributes, metadata),
                platformWithUsageAttribute(compileConfiguration, attributes, Usage.JAVA_API, false, shadowedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, Usage.JAVA_RUNTIME, false, shadowedPlatformCapability),
                platformWithUsageAttribute(compileConfiguration, attributes, Usage.JAVA_API, true, shadowedEnforcedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, Usage.JAVA_RUNTIME, true, shadowedEnforcedPlatformCapability));
        }
        return null;
    }

    /**
     * Synthesizes a "sources" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the sources-classifier jar
     */
    private DefaultConfigurationMetadata libraryWithSourcesVariant(DefaultConfigurationMetadata runtimeConfiguration, ImmutableAttributes originAttributes, ModuleComponentResolveMetadata metadata) {
        return runtimeConfiguration.mutate()
            .withName("sources")
            .withAttributes(mavenAttributesFactory.sourcesVariant(originAttributes))
            .withArtifacts(ImmutableList.of(metadata.optionalArtifact("jar", "jar", "sources")))
            .withoutConstraints()
            .build();
    }

    /**
     * Synthesizes a "javadoc" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the javadoc-classifier jar
     */
    private ModuleConfigurationMetadata libraryWithJavadocVariant(DefaultConfigurationMetadata runtimeConfiguration, ImmutableAttributes originAttributes, ModuleComponentResolveMetadata metadata) {
        return runtimeConfiguration.mutate()
            .withName("javadoc")
            .withAttributes(mavenAttributesFactory.javadocVariant(originAttributes))
            .withArtifacts(ImmutableList.of(metadata.optionalArtifact("jar", "jar", "javadoc")))
            .withoutConstraints()
            .build();
    }

    private ImmutableCapabilities buildShadowPlatformCapability(ModuleComponentIdentifier componentId, boolean enforced) {
        return ImmutableCapabilities.of(
            new ShadowedImmutableCapability(new DefaultImmutableCapability(
                    componentId.getGroup(),
                    componentId.getModule(),
                    componentId.getVersion()
            ), enforced ? "-derived-enforced-platform" : "-derived-platform")
        );
    }

    private ModuleConfigurationMetadata libraryCompileScope(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes) {
        ImmutableAttributes attributes = mavenAttributesFactory.compileScope(originAttributes);
        return conf.mutate()
            .withAttributes(attributes)
            .withoutConstraints()
            .build();
    }

    private ModuleConfigurationMetadata libraryRuntimeScope(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes) {
        ImmutableAttributes attributes = mavenAttributesFactory.runtimeScope(originAttributes);
        return conf.mutate()
            .withAttributes(attributes)
            .withoutConstraints()
            .build();
    }

    private ModuleConfigurationMetadata platformWithUsageAttribute(DefaultConfigurationMetadata conf, ImmutableAttributes originAttributes, String usage, boolean enforcedPlatform, ImmutableCapabilities shadowedPlatformCapability) {
        ImmutableAttributes attributes = mavenAttributesFactory.platformWithUsage(originAttributes, usage, enforcedPlatform);
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
