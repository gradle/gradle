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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.publish.internal.component.ResolutionBackedVariant;
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

    // TODO: Once dependency mapping is stabilized, we should be able to turn this off
    private static final boolean USE_LEGACY_VERSION_MAPPING = true;

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final AttributesSchemaInternal consumerSchema;
    private final ImmutableAttributesFactory attributesFactory;
    private final AttributeDesugaring attributeDesugaring;

    @Inject
    public DefaultDependencyCoordinateResolverFactory(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFacatory,
        AttributesSchemaInternal consumerSchema,
        ImmutableAttributesFactory attributesFactory,
        AttributeDesugaring attributeDesugaring
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.moduleIdentifierFactory = moduleIdentifierFacatory;
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
        this.attributeDesugaring = attributeDesugaring;
    }

    @Override
    public DependencyResolvers createCoordinateResolvers(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy) {
        Configuration configuration = null;
        if (variant instanceof ResolutionBackedVariant) {
            ResolutionBackedVariant resolutionBackedVariant = (ResolutionBackedVariant) variant;
            configuration = resolutionBackedVariant.getResolutionConfiguration();

            boolean useResolvedCoordinates = resolutionBackedVariant.getPublishResolvedCoordinates();
            if (useResolvedCoordinates && configuration == null) {
                throw new InvalidUserDataException("Cannot enable dependency mapping without configuring a resolution configuration.");
            } else if (useResolvedCoordinates) {

                ComponentDependencyResolver componentResolver = new ResolutionBackedComponentDependencyResolver(
                    configuration,
                    moduleIdentifierFactory,
                    projectDependencyResolver
                );

                VariantDependencyResolver variantResolver = new ResolutionBackedVariantDependencyResolver(
                    projectDependencyResolver,
                    moduleIdentifierFactory,
                    configuration,
                    consumerSchema,
                    attributesFactory,
                    attributeDesugaring,
                    componentResolver
                );

                return new DependencyResolvers(variantResolver, componentResolver);
            }
        }

        ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
        VariantVersionMappingStrategyInternal versionMapping = versionMappingStrategy.findStrategyForVariant(attributes);

        // Fallback to component coordinate mapping if variant mapping is not enabled
        ComponentDependencyResolver componentResolver = null;
        if (versionMapping.isEnabled()) {
            if (versionMapping.getUserResolutionConfiguration() != null) {
                configuration = versionMapping.getUserResolutionConfiguration();
            } else if (versionMapping.getDefaultResolutionConfiguration() != null && configuration == null) {
                // The configuration set on the variant is almost always more correct than the
                // default version mapping configuration, which is currently set project-wide
                // by the Java plugin. For this reason, we only use the version mapping default
                // if the dependency mapping configuration is not set.
                configuration = versionMapping.getDefaultResolutionConfiguration();
            }

            if (configuration != null) {
                componentResolver = USE_LEGACY_VERSION_MAPPING
                    ? new VersionMappingVariantDependencyResolver(projectDependencyResolver, configuration)
                    : new ResolutionBackedComponentDependencyResolver(configuration, moduleIdentifierFactory, projectDependencyResolver);
            }
        }

        if (componentResolver == null) {
            // Both version mapping and dependency mapping are disabled
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
    @VisibleForTesting
    static class ProjectOnlyComponentDependencyResolver implements ComponentDependencyResolver {

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
            return ResolvedCoordinates.create(projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath));
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
