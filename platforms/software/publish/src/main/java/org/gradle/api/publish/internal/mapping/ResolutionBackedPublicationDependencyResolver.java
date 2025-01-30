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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@link VariantDependencyResolver} that analyzes a resolution result to determine the
 * resolved coordinates for a given dependency.
 *
 * <p>The configuration being resolved should declare the same dependencies as the variant
 * being published. Then, each outgoing edge of the analyzed resolution result will correspond
 * to each declared dependency on the published variant. We build a mapping from requested
 * coordinates to resolved coordinates. Then, when resolving individual variant or component
 * coordinates, we can look up in the map to determine what coordinates should be published
 * for a given declared dependency.</p>
 */
public class ResolutionBackedPublicationDependencyResolver implements VariantDependencyResolver, ComponentDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final AttributeDesugaring attributeDesugaring;

    private final ResolvedMappings mappings;

    public ResolutionBackedPublicationDependencyResolver(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ResolvedComponentResult rootComponent,
        ResolvedVariantResult rootVariant,
        AttributeDesugaring attributeDesugaring
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.attributeDesugaring = attributeDesugaring;

        this.mappings = calculateMappings(rootComponent, rootVariant, projectDependencyResolver, moduleIdentifierFactory);
    }

    private static ResolvedMappings calculateMappings(
        ResolvedComponentResult rootComponent,
        ResolvedVariantResult rootVariant,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        Set<ModuleDependencyKey> incompatibleModuleDeps = new HashSet<>();
        Set<ProjectDependencyKey> incompatibleProjectDeps = new HashSet<>();

        Map<ModuleIdentifier, ModuleVersionIdentifier> resolvedModuleComponents = new HashMap<>();
        Map<Path, ModuleVersionIdentifier> resolvedProjectComponents = new HashMap<>();

        Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModuleVariants = new HashMap<>();
        Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjectVariants = new HashMap<>();

        visitFirstLevelEdges(rootComponent, rootVariant, edge -> {

            ComponentSelector requested = edge.getRequested();
            CoordinatePair coordinates = getResolvedCoordinates(edge.getResolvedVariant(), projectDependencyResolver, moduleIdentifierFactory);
            if (requested instanceof ModuleComponentSelector) {
                ModuleComponentSelector requestedModule = (ModuleComponentSelector) requested;

                ModuleVersionIdentifier existingComponent = resolvedModuleComponents.put(requestedModule.getModuleIdentifier(), coordinates.componentCoordinates);
                if (existingComponent != null && !existingComponent.equals(coordinates.componentCoordinates)) {
                    throw new GradleException("Expected all requested coordinates to resolve to the same component coordinates.");
                }

                ModuleDependencyKey key = new ModuleDependencyKey(requestedModule.getModuleIdentifier(), ModuleDependencyDetails.from(requested));
                if (incompatibleModuleDeps.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existingVariant = resolvedModuleVariants.put(key, coordinates.variantCoordinates);
                if (existingVariant != null && !existingVariant.equals(coordinates.variantCoordinates)) {
                    resolvedModuleVariants.remove(key);
                    incompatibleModuleDeps.add(key);
                }
            } else if (requested instanceof ProjectComponentSelector) {
                ProjectComponentSelectorInternal requestedProject = (ProjectComponentSelectorInternal) requested;

                ModuleVersionIdentifier existingComponent = resolvedProjectComponents.put(requestedProject.getIdentityPath(), coordinates.componentCoordinates);
                if (existingComponent != null && !existingComponent.equals(coordinates.componentCoordinates)) {
                    throw new GradleException("Expected all requested projects to resolve to the same component coordinates.");
                }

                ProjectDependencyKey key = new ProjectDependencyKey(requestedProject.getIdentityPath(), ModuleDependencyDetails.from(requested));
                if (incompatibleProjectDeps.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existingVariant = resolvedProjectVariants.put(key, coordinates.variantCoordinates);
                if (existingVariant != null && !existingVariant.equals(coordinates.variantCoordinates)) {
                    resolvedProjectVariants.remove(key);
                    incompatibleProjectDeps.add(key);
                }
            }

        });

        return new ResolvedMappings(
            resolvedModuleComponents,
            resolvedProjectComponents,
            resolvedModuleVariants,
            resolvedProjectVariants,
            incompatibleModuleDeps,
            incompatibleProjectDeps
        );
    }

    private static void visitFirstLevelEdges(ResolvedComponentResult rootComponent, ResolvedVariantResult rootVariant, Consumer<ResolvedDependencyResult> visitor) {
        List<DependencyResult> rootEdges = rootComponent.getDependenciesForVariant(rootVariant);
        for (DependencyResult dependencyResult : rootEdges) {
            if (!(dependencyResult instanceof ResolvedDependencyResult)) {
                UnresolvedDependencyResult unresolved = (UnresolvedDependencyResult) dependencyResult;
                throw new GradleException("Could not map coordinates for " + unresolved.getAttempted().getDisplayName() + ".", unresolved.getFailure());
            }

            if (dependencyResult.isConstraint()) {
                // Constraints also appear in the graph if they contributed to it.
                // Ignore them for now, though perhaps we can use them in the future to
                // publish version ranges.
                continue;
            }

            visitor.accept((ResolvedDependencyResult) dependencyResult);
        }
    }

    private static CoordinatePair getResolvedCoordinates(
        ResolvedVariantResult variant,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        ComponentIdentifier componentId = variant.getOwner();

        // TODO #3170: We should analyze artifacts to determine if we need to publish additional
        // artifact information like type or classifier.

        if (componentId instanceof ProjectComponentIdentifier) {
            return getProjectCoordinates(variant, (ProjectComponentIdentifierInternal) componentId, projectDependencyResolver);
        } else if (componentId instanceof ModuleComponentIdentifier) {
            return getModuleCoordinates(variant, (ModuleComponentIdentifier) componentId, moduleIdentifierFactory);
        } else {
            throw new UnsupportedOperationException("Unexpected component identifier type: " + componentId);
        }
    }

    private static CoordinatePair getModuleCoordinates(
        ResolvedVariantResult variant,
        ModuleComponentIdentifier componentId,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        ModuleVersionIdentifier componentCoordinates = moduleIdentifierFactory.moduleWithVersion(componentId.getModuleIdentifier(), componentId.getVersion());
        ModuleVersionIdentifier variantCoordinates = getExternalCoordinates(variant, moduleIdentifierFactory);

        return new CoordinatePair(
            componentCoordinates,
            variantCoordinates != null ? variantCoordinates : componentCoordinates
        );
    }

    private static @Nullable ModuleVersionIdentifier getExternalCoordinates(
        ResolvedVariantResult variant,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        ResolvedVariantResult externalVariant = variant.getExternalVariant().orElse(null);
        if (externalVariant != null) {
            ComponentIdentifier owningComponent = externalVariant.getOwner();
            if (owningComponent instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) owningComponent;
                return moduleIdentifierFactory.moduleWithVersion(moduleComponentId.getModuleIdentifier(), moduleComponentId.getVersion());
            }
            throw new GradleException("Expected owning component of module component to be a module component: " + owningComponent);
        }
        return null;
    }

    private static CoordinatePair getProjectCoordinates(
        ResolvedVariantResult variant,
        ProjectComponentIdentifierInternal componentId,
        ProjectDependencyPublicationResolver projectDependencyResolver
    ) {
        Path identityPath = componentId.getIdentityPath();

        // TODO: Using the display name here is not great, however it is the same as the variant name.
        // Instead, the resolution result should expose the project coordinates via getExternalVariant.
        String variantName = variant.getDisplayName();
        ModuleVersionIdentifier variantCoordinates = projectDependencyResolver.resolveVariant(ModuleVersionIdentifier.class, identityPath, variantName);

        if (variantCoordinates == null) {
            throw new InvalidUserDataException(String.format(
                "Could not resolve coordinates for variant '%s' of project '%s'.",
                variantName, identityPath
            ));
        }

        ModuleVersionIdentifier componentCoordinates = projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        return new CoordinatePair(componentCoordinates, variantCoordinates);
    }

    @Override
    @Nullable
    public ResolvedCoordinates resolveVariantCoordinates(ExternalDependency dependency, VariantWarningCollector warnings) {
        ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
        ModuleDependencyKey key = new ModuleDependencyKey(module, ModuleDependencyDetails.from(dependency, attributeDesugaring));

        ModuleVersionIdentifier resolved = mappings.resolvedModuleVariants.get(key);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }

        if (mappings.incompatibleModules.contains(key)) {
            // TODO: We should enhance this warning to list the conflicting dependencies.
            warnings.addIncompatible(String.format(
                "Cannot determine variant coordinates for dependency '%s' since " +
                    "multiple dependencies ambiguously map to different resolved coordinates.",
                module
            ));
        } else {
            // This is likely user error, as the resolution result should have the same dependencies as the published variant.
            warnings.addIncompatible(String.format(
                "Cannot determine variant coordinates for dependency '%s' since " +
                    "the resolved graph does not contain the requested module.",
                module
            ));
        }

        // Fallback to component coordinate mapping only.
        return resolveModuleComponentCoordinates(module);
    }

    @Override
    public ResolvedCoordinates resolveVariantCoordinates(ProjectDependency dependency, VariantWarningCollector warnings) {
        Path identityPath = ((ProjectDependencyInternal) dependency).getTargetProjectIdentity().getBuildTreePath();
        ProjectDependencyKey key = new ProjectDependencyKey(identityPath, ModuleDependencyDetails.from(dependency, attributeDesugaring));

        ModuleVersionIdentifier resolved = mappings.resolvedProjectVariants.get(key);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }

        if (mappings.incompatibleProjects.contains(key)) {
            // TODO: We should enhance this warning to list the conflicting dependencies.
            warnings.addIncompatible(String.format(
                "Cannot determine variant coordinates for Project dependency '%s' since " +
                    "multiple dependencies ambiguously map to different resolved coordinates.",
                identityPath
            ));
        } else {
            // This is likely user error, as the resolution result should have the same dependencies as the published variant.
            warnings.addIncompatible(String.format(
                "Cannot determine variant coordinates for Project dependency '%s' since " +
                    "the resolved graph does not contain the requested project.",
                identityPath
            ));
        }

        // Fallback to component coordinate mapping only.
        return resolveComponentCoordinates(dependency);
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ExternalDependency dependency) {
        ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
        return resolveModuleComponentCoordinates(module);
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ProjectDependency dependency) {
        Path identityPath = ((ProjectDependencyInternal) dependency).getTargetProjectIdentity().getBuildTreePath();
        ModuleVersionIdentifier resolved = mappings.resolvedProjectComponents.get(identityPath);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }

        // This is likely user error, as the resolution result should have the same
        // dependencies as the published variant. Fallback to resolving the project
        // coordinates directly.

        return ResolvedCoordinates.create(
            projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath)
        );
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency) {
        assert !(dependency instanceof DefaultProjectDependencyConstraint);
        ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
        return resolveModuleComponentCoordinates(module);
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DefaultProjectDependencyConstraint dependency) {
        return resolveComponentCoordinates(dependency.getProjectDependency());
    }


    @Nullable
    private ResolvedCoordinates resolveModuleComponentCoordinates(ModuleIdentifier module) {
        ModuleVersionIdentifier resolved = mappings.resolvedModuleComponents.get(module);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }
        return null;
    }

    private static class ResolvedMappings {

        final Map<ModuleIdentifier, ModuleVersionIdentifier> resolvedModuleComponents;
        final Map<Path, ModuleVersionIdentifier> resolvedProjectComponents;

        final Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModuleVariants;
        final Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjectVariants;

        // Incompatible modules and projects are those that have multiple dependencies with the same
        // attributes and capabilities, but have somehow resolved to different coordinates. This can
        // often happen when the dependency is declared with a targetConfiguration.
        final Set<ModuleDependencyKey> incompatibleModules;
        final Set<ProjectDependencyKey> incompatibleProjects;

        ResolvedMappings(
            Map<ModuleIdentifier, ModuleVersionIdentifier> resolvedModuleComponents,
            Map<Path, ModuleVersionIdentifier> resolvedProjectComponents,
            Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModuleVariants,
            Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjectVariants,
            Set<ModuleDependencyKey> incompatibleModules,
            Set<ProjectDependencyKey> incompatibleProjects
        ) {
            this.resolvedModuleComponents = resolvedModuleComponents;
            this.resolvedProjectComponents = resolvedProjectComponents;
            this.resolvedModuleVariants = resolvedModuleVariants;
            this.resolvedProjectVariants = resolvedProjectVariants;
            this.incompatibleModules = incompatibleModules;
            this.incompatibleProjects = incompatibleProjects;
        }
    }

    private static class CoordinatePair {
        final ModuleVersionIdentifier componentCoordinates;
        final ModuleVersionIdentifier variantCoordinates;

        private CoordinatePair(
            ModuleVersionIdentifier componentCoordinates,
            ModuleVersionIdentifier variantCoordinates
        ) {
            this.componentCoordinates = componentCoordinates;
            this.variantCoordinates = variantCoordinates;
        }
    }

    private static class ModuleDependencyKey {
        private final ModuleIdentifier module;
        private final ModuleDependencyDetails details;

        public ModuleDependencyKey(ModuleIdentifier module, ModuleDependencyDetails details) {
            this.module = module;
            this.details = details;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ModuleDependencyKey that = (ModuleDependencyKey) o;
            return Objects.equals(module, that.module) && Objects.equals(details, that.details);
        }

        @Override
        public int hashCode() {
            return Objects.hash(module, details);
        }
    }

    private static class ProjectDependencyKey {
        private final Path identityPath;
        private final ModuleDependencyDetails details;

        public ProjectDependencyKey(Path identityPath, ModuleDependencyDetails details) {
            this.identityPath = identityPath;
            this.details = details;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectDependencyKey that = (ProjectDependencyKey) o;
            return Objects.equals(identityPath, that.identityPath) && Objects.equals(details, that.details);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identityPath, details);
        }
    }

    private static class ModuleDependencyDetails {
        final AttributeContainer requestAttributes;
        final Set<CapabilitySelector> capabilitySelectors;

        public ModuleDependencyDetails(
            AttributeContainer requestAttributes,
            Set<CapabilitySelector> capabilitySelectors
        ) {
            this.requestAttributes = requestAttributes;
            this.capabilitySelectors = capabilitySelectors;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ModuleDependencyDetails that = (ModuleDependencyDetails) o;
            return Objects.equals(requestAttributes, that.requestAttributes) && Objects.equals(capabilitySelectors, that.capabilitySelectors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestAttributes, capabilitySelectors);
        }

        public static ModuleDependencyDetails from(ModuleDependency dependency, AttributeDesugaring attributeDesugaring) {
            ImmutableAttributes attributes = ((AttributeContainerInternal) dependency.getAttributes()).asImmutable();
            return new ModuleDependencyDetails(
                attributeDesugaring.desugar(attributes),
                dependency.getCapabilitySelectors()
            );
        }

        // Do not desugar here since resolution results already expose desugared attributes.
        public static ModuleDependencyDetails from(ComponentSelector componentSelector) {
            return new ModuleDependencyDetails(
                componentSelector.getAttributes(),
                componentSelector.getCapabilitySelectors()
            );
        }
    }
}
