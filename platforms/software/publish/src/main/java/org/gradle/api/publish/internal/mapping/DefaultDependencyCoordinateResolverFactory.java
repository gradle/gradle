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
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
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

    /**
     * Determines whether we implement publication versionMapping with the legacy implementation
     * or the new dependency mapping implementation.
     *
     * TODO: While this is currently static, we should selectively enable it in order to run
     *       versionMapping tests against both implementations.
     *
     * TODO: Once dependency mapping is stabilized, we should be able to turn this off / remove it entirely
     */
    private static final boolean USE_LEGACY_VERSION_MAPPING = true;

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final AttributeDesugaring attributeDesugaring;

    @Inject
    public DefaultDependencyCoordinateResolverFactory(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        AttributeDesugaring attributeDesugaring
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.attributeDesugaring = attributeDesugaring;
    }

    @Override
    public Provider<DependencyResolvers> createCoordinateResolvers(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy) {
        Configuration configuration = null;
        if (variant instanceof ResolutionBackedVariant) {
            ResolutionBackedVariant resolutionBackedVariant = (ResolutionBackedVariant) variant;
            configuration = resolutionBackedVariant.getResolutionConfiguration();

            boolean useResolvedCoordinates = resolutionBackedVariant.getPublishResolvedCoordinates();
            if (useResolvedCoordinates && configuration == null) {
                throw new InvalidUserDataException("Cannot enable dependency mapping without configuring a resolution configuration.");
            } else if (useResolvedCoordinates) {
                ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
                return resolutionResult.getRootComponent().zip(resolutionResult.getRootVariant(), this::getVariantMappingResolvers);
            }
        }

        ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
        VariantVersionMappingStrategyInternal versionMapping = versionMappingStrategy.findStrategyForVariant(attributes);

        // Fallback to component coordinate mapping if variant mapping is not enabled
        Provider<? extends ComponentDependencyResolver> componentResolver = null;
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
                componentResolver = configuration.getIncoming().getResolutionResult().getRootComponent()
                    .map(this::getComponentMappingResolver);
            }
        }

        if (componentResolver == null) {
            // Both version mapping and dependency mapping are disabled
            componentResolver = Providers.of(new ProjectOnlyComponentDependencyResolver(projectDependencyResolver));
        }

        return componentResolver.map(cr -> new DependencyResolvers(new VariantResolverAdapter(cr), cr));
    }

    private ComponentDependencyResolver getComponentMappingResolver(ResolvedComponentResult root) {
        if (USE_LEGACY_VERSION_MAPPING) {
            return new VersionMappingComponentDependencyResolver(projectDependencyResolver, root);
        } else {
            return new ResolutionBackedComponentDependencyResolver(
                root,
                moduleIdentifierFactory,
                projectDependencyResolver
            );
        }
    }

    private DependencyResolvers getVariantMappingResolvers(ResolvedComponentResult rootComponent, ResolvedVariantResult rootVariant) {
        ComponentDependencyResolver componentResolver =
            new ResolutionBackedComponentDependencyResolver(rootComponent, moduleIdentifierFactory, projectDependencyResolver);

        VariantDependencyResolver variantResolver = new ResolutionBackedVariantDependencyResolver(
            projectDependencyResolver,
            moduleIdentifierFactory,
            rootComponent,
            rootVariant,
            attributeDesugaring,
            componentResolver
        );

        return new DependencyResolvers(variantResolver, componentResolver);
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
