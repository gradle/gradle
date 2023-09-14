/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;

/**
 * Default implementation of {@link VariantDependencyResolverFactory} that
 * resolves dependencies using version mapping.
 */
public class DefaultVariantDependencyResolverFactory implements VariantDependencyResolverFactory {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final VersionMappingStrategyInternal versionMappingStrategy;

    public DefaultVariantDependencyResolverFactory(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        VersionMappingStrategyInternal versionMappingStrategy
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.versionMappingStrategy = versionMappingStrategy;
    }

    @Override
    public VariantDependencyResolver createResolver(
        SoftwareComponentVariant variant,
        DeclaredVersionTransformer declaredVersionTransformer
    ) {
        ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
        VariantVersionMappingStrategyInternal versionMapping = versionMappingStrategy.findStrategyForVariant(attributes);

        Configuration configuration = null;
        if (versionMapping.isEnabled()) {
            if (versionMapping.getUserResolutionConfiguration() != null) {
                configuration = versionMapping.getUserResolutionConfiguration();
            } else {
                configuration = versionMapping.getDefaultResolutionConfiguration();
            }
        }

        return new VersionMappingVariantDependencyResolver(
            projectDependencyResolver,
            configuration,
            declaredVersionTransformer
        );
    }

}
