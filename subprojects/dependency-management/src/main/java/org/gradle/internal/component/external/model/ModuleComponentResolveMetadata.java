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
package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;

import javax.annotation.Nullable;

/**
 * The meta-data for a component that is resolved from a module in a binary repository.
 *
 * <p>Implementations of this type should be immutable and thread safe.</p>
 */
public interface ModuleComponentResolveMetadata extends ComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    @Override
    ModuleComponentIdentifier getId();

    /**
     * Creates a mutable copy of this metadata.
     *
     * Note that this method can be expensive. Often it is more efficient to use a more specialised mutation method such as {@link #withSources(ModuleSources)} rather than this method.
     */
    MutableModuleComponentResolveMetadata asMutable();

    /**
     * {@inheritDoc}
     */
    @Override
    ModuleComponentResolveMetadata withSources(ModuleSources sources);


    /**
     * Creates a copy of this meta-data with the given derivation strategy.
     */
    ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy);


    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);

    ModuleComponentArtifactMetadata optionalArtifact(String type, @Nullable String extension, @Nullable String classifier);

    /**
     * Returns the variants of this component
     */
    ImmutableList<? extends ComponentVariant> getVariants();

    @Nullable
    ImmutableAttributesFactory getAttributesFactory();

    VariantMetadataRules getVariantMetadataRules();

    VariantDerivationStrategy getVariantDerivationStrategy();

    boolean isExternalVariant();

    /*
     * When set to false component metadata rules are not cached.
     * Currently, we disable it just for local maven/ivy repository.
     *
     * Default value is true.
     */
    boolean isComponentMetadataRuleCachingEnabled();

}
