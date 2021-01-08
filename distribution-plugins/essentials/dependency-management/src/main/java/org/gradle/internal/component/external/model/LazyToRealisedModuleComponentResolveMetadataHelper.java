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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class to help transform a lazy {@link ModuleComponentResolveMetadata} into a realised one.
 */
public class LazyToRealisedModuleComponentResolveMetadataHelper {
    /**
     * Method to transform lazy variants into realised ones
     *
     * @param mutableMetadata the source metadata
     * @param variantMetadataRules the lazy rules
     * @param variants the variants to transform
     * @return a list of realised variants
     */
    public static ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> realiseVariants(ModuleComponentResolveMetadata mutableMetadata, VariantMetadataRules variantMetadataRules, ImmutableList<? extends ComponentVariant> variants) {
        if (variants.isEmpty()) {
            return ImmutableList.of();
        }
        List<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> realisedVariants = Lists.newArrayListWithExpectedSize(variants.size());
        for (ComponentVariant variant : variants) {
            realisedVariants.add(applyRules(variant, variantMetadataRules, mutableMetadata.getId()));
        }
        return addVariantsFromRules(mutableMetadata, realisedVariants, variantMetadataRules);
    }

    private static ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> addVariantsFromRules(ModuleComponentResolveMetadata componentMetadata, List<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> declaredVariants, VariantMetadataRules variantMetadataRules) {
        List<AdditionalVariant> additionalVariants = variantMetadataRules.getAdditionalVariants();
        if (additionalVariants.isEmpty()) {
            return ImmutableList.copyOf(declaredVariants);
        }

        ImmutableList.Builder<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> builder = new ImmutableList.Builder<>();
        builder.addAll(declaredVariants);
        Map<String, ComponentVariant> variantsByName = declaredVariants.stream().collect(Collectors.toMap(ComponentVariant::getName, Function.identity()));
        for (AdditionalVariant additionalVariant : additionalVariants) {
            String baseName = additionalVariant.getBase();
            ImmutableAttributes attributes;
            ImmutableCapabilities capabilities;
            ImmutableList<? extends ComponentVariant.Dependency> dependencies;
            ImmutableList<? extends ComponentVariant.DependencyConstraint> dependencyConstraints;
            ImmutableList<? extends ComponentVariant.File> files;

            ComponentVariant baseVariant = variantsByName.get(baseName);
            boolean isExternalVariant;
            if (baseVariant == null) {
                attributes = componentMetadata.getAttributes();
                capabilities = ImmutableCapabilities.EMPTY;
                dependencies = ImmutableList.of();
                dependencyConstraints = ImmutableList.of();
                files = ImmutableList.of();
                isExternalVariant = false;
            } else {
                attributes = (ImmutableAttributes) baseVariant.getAttributes();
                capabilities = (ImmutableCapabilities) baseVariant.getCapabilities();
                dependencies = baseVariant.getDependencies();
                dependencyConstraints = baseVariant.getDependencyConstraints();
                files = baseVariant.getFiles();
                isExternalVariant = baseVariant.isExternalVariant();
            }

            if (baseName == null || baseVariant != null) {
                AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl variant = applyRules(new AbstractMutableModuleComponentResolveMetadata.ImmutableVariantImpl(
                        componentMetadata.getId(), additionalVariant.getName(), attributes, dependencies, dependencyConstraints, files, capabilities, isExternalVariant),
                    variantMetadataRules, componentMetadata.getId());
                builder.add(variant);
            } else if (!additionalVariant.isLenient()) {
                throw new InvalidUserDataException("Variant '" + baseName + "' not defined in module " + componentMetadata.getId().getDisplayName());
            }
        }
        return builder.build();
    }

    private static AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl applyRules(ComponentVariant variant, VariantMetadataRules variantMetadataRules, ModuleComponentIdentifier id) {
        ImmutableAttributes attributes = variantMetadataRules.applyVariantAttributeRules(variant, variant.getAttributes());
        CapabilitiesMetadata capabilitiesMetadata = variantMetadataRules.applyCapabilitiesRules(variant, variant.getCapabilities());
        ImmutableList<? extends ComponentVariant.File> files = variantMetadataRules.applyVariantFilesMetadataRulesToFiles(variant, variant.getFiles(), id);
        boolean force = PlatformSupport.hasForcedDependencies(variant);
        List<GradleDependencyMetadata> dependencies = variantMetadataRules.applyDependencyMetadataRules(variant, convertDependencies(variant.getDependencies(), variant.getDependencyConstraints(), force));
        return new AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl(id, variant.getName(), attributes,
            variant.getDependencies(), variant.getDependencyConstraints(), files,
            ImmutableCapabilities.of(capabilitiesMetadata.getCapabilities()), dependencies, variant.isExternalVariant());
    }

    private static List<GradleDependencyMetadata> convertDependencies(List<? extends ComponentVariant.Dependency> dependencies, List<? extends ComponentVariant.DependencyConstraint> dependencyConstraints, boolean force) {
        List<GradleDependencyMetadata> result = new ArrayList<>(dependencies.size());
        for (ComponentVariant.Dependency dependency : dependencies) {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getModule()), dependency.getVersionConstraint(), dependency.getAttributes(), dependency.getRequestedCapabilities());
            List<ExcludeMetadata> excludes = dependency.getExcludes();
            result.add(new GradleDependencyMetadata(selector, excludes, false, dependency.isEndorsingStrictVersions(), dependency.getReason(), force, dependency.getDependencyArtifact()));
        }
        for (ComponentVariant.DependencyConstraint dependencyConstraint : dependencyConstraints) {
            result.add(new GradleDependencyMetadata(
                DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependencyConstraint.getGroup(), dependencyConstraint.getModule()), dependencyConstraint.getVersionConstraint(), dependencyConstraint.getAttributes(), ImmutableList.of()),
                Collections.emptyList(),
                true,
                false,
                dependencyConstraint.getReason(),
                force,
                null
            ));
        }
        return result;
    }

    public static ImmutableSet<String> constructHierarchy(Configuration descriptorConfiguration, ImmutableMap<String, Configuration> configurationDefinitions) {
        if (descriptorConfiguration.getExtendsFrom().isEmpty()) {
            return ImmutableSet.of(descriptorConfiguration.getName());
        }
        ImmutableSet.Builder<String> accumulator = new ImmutableSet.Builder<>();
        populateHierarchy(descriptorConfiguration, configurationDefinitions, accumulator);
        return accumulator.build();
    }

    private static void populateHierarchy(Configuration metadata, ImmutableMap<String, Configuration> configurationDefinitions, ImmutableSet.Builder<String> accumulator) {
        accumulator.add(metadata.getName());
        for (String parentName : metadata.getExtendsFrom()) {
            Configuration parent = configurationDefinitions.get(parentName);
            populateHierarchy(parent, configurationDefinitions, accumulator);
        }
    }

}
