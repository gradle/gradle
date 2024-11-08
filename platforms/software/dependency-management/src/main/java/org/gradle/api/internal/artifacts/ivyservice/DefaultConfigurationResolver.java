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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributeContainerInternal;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for resolving a configuration. Delegates to a {@link ResolutionExecutor} to perform
 * the actual resolution.
 */
public class DefaultConfigurationResolver implements ConfigurationResolver {
    private final RepositoriesSupplier repositoriesSupplier;
    private final ResolutionExecutor resolutionExecutor;

    public DefaultConfigurationResolver(
        RepositoriesSupplier repositoriesSupplier,
        ResolutionExecutor resolutionExecutor
    ) {
        this.repositoriesSupplier = repositoriesSupplier;
        this.resolutionExecutor = resolutionExecutor;
    }

    @Override
    public ResolverResults resolveBuildDependencies(ResolveContext resolveContext) {
        return resolutionExecutor.resolveBuildDependencies(resolveContext);
    }

    @Override
    public ResolverResults resolveGraph(ResolveContext resolveContext) {
        AttributeContainerInternal attributes = resolveContext.toRootComponent().getRootVariant().getAttributes();

        List<ResolutionAwareRepository> filteredRepositories = repositoriesSupplier.get().stream()
            .filter(repository -> !shouldSkipRepository(repository, resolveContext.getName(), attributes))
            .collect(Collectors.toList());

        return resolutionExecutor.resolveGraph(resolveContext, filteredRepositories);
    }

    @Override
    public List<ResolutionAwareRepository> getAllRepositories() {
        return repositoriesSupplier.get();
    }

    /**
     * Determines if the repository should not be used to resolve this configuration.
     */
    private static boolean shouldSkipRepository(
        ResolutionAwareRepository repository,
        String resolveContextName,
        AttributeContainer consumerAttributes
    ) {
        if (!(repository instanceof ContentFilteringRepository)) {
            return false;
        }

        ContentFilteringRepository cfr = (ContentFilteringRepository) repository;

        Set<String> includedConfigurations = cfr.getIncludedConfigurations();
        Set<String> excludedConfigurations = cfr.getExcludedConfigurations();

        if ((includedConfigurations != null && !includedConfigurations.contains(resolveContextName)) ||
            (excludedConfigurations != null && excludedConfigurations.contains(resolveContextName))
        ) {
            return true;
        }

        Map<Attribute<Object>, Set<Object>> requiredAttributes = cfr.getRequiredAttributes();
        return hasNonRequiredAttribute(requiredAttributes, consumerAttributes);
    }

    /**
     * Accepts a map of attribute types to the set of values that are allowed for that attribute type.
     * If the request attributes of the resolve context being resolved do not match the allowed values,
     * then the repository is skipped.
     */
    private static boolean hasNonRequiredAttribute(
        @Nullable Map<Attribute<Object>, Set<Object>> requiredAttributes,
        AttributeContainer consumerAttributes
    ) {
        if (requiredAttributes == null) {
            return false;
        }

        for (Map.Entry<Attribute<Object>, Set<Object>> entry : requiredAttributes.entrySet()) {
            Attribute<Object> key = entry.getKey();
            Set<Object> allowedValues = entry.getValue();
            Object value = consumerAttributes.getAttribute(key);
            if (!allowedValues.contains(value)) {
                return true;
            }
        }

        return false;
    }
}
