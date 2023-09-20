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
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Default implementation of {@link DependencyCoordinateResolverFactory} that
 * resolves dependencies using version mapping.
 */
public class DefaultDependencyCoordinateResolverFactory implements DependencyCoordinateResolverFactory {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;

    @Inject
    public DefaultDependencyCoordinateResolverFactory(ProjectDependencyPublicationResolver projectDependencyResolver) {
        this.projectDependencyResolver = projectDependencyResolver;
    }

    @Override
    public DependencyResolvers createCoordinateResolvers(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy) {
        Configuration configuration = null;

        ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
        VariantVersionMappingStrategyInternal versionMapping = versionMappingStrategy.findStrategyForVariant(attributes);

        // Fallback to component coordinate mapping if variant mapping is not enabled
        ComponentDependencyResolver componentResolver;
        if (versionMapping.isEnabled()) {
            if (versionMapping.getUserResolutionConfiguration() != null) {
                configuration = versionMapping.getUserResolutionConfiguration();
            } else if (versionMapping.getDefaultResolutionConfiguration() != null) {
                configuration = versionMapping.getDefaultResolutionConfiguration();
            }
        }

        if (configuration != null) {
            componentResolver = new VersionMappingVariantDependencyResolver(projectDependencyResolver, configuration);
        } else {
            componentResolver = new ProjectOnlyComponentDependencyResolver(projectDependencyResolver);
        }

        return new DependencyResolvers(new VariantResolverAdapter(componentResolver), componentResolver);
    }

    /**
     * Adapts a {@link ComponentDependencyResolver} to a {@link VariantDependencyResolver}
     * by returning component-precision coordinates.
     */
    private static class VariantResolverAdapter implements VariantDependencyResolver {

        private final ComponentDependencyResolver delegate;

        public VariantResolverAdapter(ComponentDependencyResolver delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public ResolvedCoordinates resolveVariantCoordinates(ExternalDependency dependency, VariantWarningCollector warnings) {
            return delegate.resolveComponentCoordinates(dependency);
        }

        @Override
        public ResolvedCoordinates resolveVariantCoordinates(ProjectDependency dependency, VariantWarningCollector warnings) {
            return delegate.resolveComponentCoordinates(dependency);
        }
    }

    /**
     * A {@link ComponentDependencyResolver} which does not depend on resolving a dependency graph.
     */
    private static class ProjectOnlyComponentDependencyResolver implements ComponentDependencyResolver {

        private final ProjectDependencyPublicationResolver projectDependencyResolver;

        public ProjectOnlyComponentDependencyResolver(ProjectDependencyPublicationResolver projectDependencyResolver) {
            this.projectDependencyResolver = projectDependencyResolver;
        }

        @Nullable
        @Override
        public ResolvedCoordinates resolveComponentCoordinates(ExternalDependency dependency) {
            return null;
        }

        @Override
        public ResolvedCoordinates resolveComponentCoordinates(ProjectDependency dependency) {
            Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
            return ResolvedCoordinates.create(projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath));
        }

        @Nullable
        @Override
        public ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency) {
            return null;
        }

        @Override
        public ResolvedCoordinates resolveComponentCoordinates(DefaultProjectDependencyConstraint dependency) {
            return resolveComponentCoordinates(dependency.getProjectDependency());
        }
    }
}
