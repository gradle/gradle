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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.internal.lazy.Lazy;
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
 * coordinates to resolved coordinates. Then, when resolving individual variant coordinates,
 * we can look up in the map to determine what coordinates should be published for a given
 * declared dependency.</p>
 */
public class ResolutionBackedVariantDependencyResolver implements VariantDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final Configuration resolutionConfiguration;
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentDependencyResolver fallback;

    private final Lazy<ResolvedMappings> mappings;

    public ResolutionBackedVariantDependencyResolver(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        Configuration resolutionConfiguration,
        AttributeDesugaring attributeDesugaring,
        ComponentDependencyResolver fallback
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.resolutionConfiguration = resolutionConfiguration;
        this.attributeDesugaring = attributeDesugaring;
        this.fallback = fallback;

        this.mappings = Lazy.unsafe().of(this::calculateMappings);
    }

    private ResolvedMappings calculateMappings() {
        Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules = new HashMap<>();
        Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects = new HashMap<>();
        Set<ModuleDependencyKey> incompatibleModules = new HashSet<>();
        Set<ProjectDependencyKey> incompatibleProjects = new HashSet<>();

        ResolutionResult resolutionResult = resolutionConfiguration.getIncoming().getResolutionResult();
        ResolvedComponentResult rootComponent = resolutionResult.getRoot();
        ResolvedVariantResult rootVariant = rootComponent.getVariants().stream()
            .filter(x -> x.getDisplayName().equals(resolutionConfiguration.getName()))
            .findFirst().get();

        visitFirstLevelEdges(rootComponent, rootVariant, edge -> {

            ComponentSelector requested = edge.getRequested();
            ModuleVersionIdentifier coordinates = getVariantCoordinates(edge);
            if (requested instanceof ModuleComponentSelector) {
                ModuleComponentSelector requestedModule = (ModuleComponentSelector) requested;

                ModuleDependencyKey key = new ModuleDependencyKey(requestedModule.getModuleIdentifier(), ModuleDependencyDetails.from(requested));
                if (incompatibleModules.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existing = resolvedModules.put(key, coordinates);
                if (existing != null && !existing.equals(coordinates)) {
                    resolvedModules.remove(key);
                    incompatibleModules.add(key);
                }
            } else if (requested instanceof ProjectComponentSelector) {
                ProjectComponentSelectorInternal requestedProject = (ProjectComponentSelectorInternal) requested;

                ProjectDependencyKey key = new ProjectDependencyKey(requestedProject.getIdentityPath(), ModuleDependencyDetails.from(requested));
                if (incompatibleProjects.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existing = resolvedProjects.put(key, coordinates);
                if (existing != null && !existing.equals(coordinates)) {
                    resolvedProjects.remove(key);
                    incompatibleProjects.add(key);
                }
            }

        });

        return new ResolvedMappings(resolvedModules, resolvedProjects, incompatibleModules, incompatibleProjects);
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

    private ModuleVersionIdentifier getVariantCoordinates(ResolvedDependencyResult edge) {
        ResolvedVariantResult variant = edge.getResolvedVariant();
        ComponentIdentifier componentId = variant.getOwner();

        // TODO #3170: We should analyze artifacts to determine if we need to publish additional
        // artifact information like type or classifier.

        if (componentId instanceof ProjectComponentIdentifier) {
            return getProjectCoordinates(variant, (ProjectComponentIdentifierInternal) componentId);
        } else if (componentId instanceof ModuleComponentIdentifier) {
            return getModuleCoordinates(variant, (ModuleComponentIdentifier) componentId);
        } else {
            throw new UnsupportedOperationException("Unexpected component identifier type: " + componentId);
        }
    }

    private ModuleVersionIdentifier getModuleCoordinates(ResolvedVariantResult variant, ModuleComponentIdentifier componentId) {
        ResolvedVariantResult externalVariant = variant.getExternalVariant().orElse(null);
        if (externalVariant != null) {
            ComponentIdentifier owningComponent = externalVariant.getOwner();
            if (owningComponent instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) owningComponent;
                return moduleIdentifierFactory.moduleWithVersion(moduleComponentId.getModuleIdentifier(), moduleComponentId.getVersion());
            }
            throw new InvalidUserDataException("Expected owning component of module component to be a module component: " + owningComponent);
        }

        return moduleIdentifierFactory.moduleWithVersion(componentId.getModuleIdentifier(), componentId.getVersion());
    }

    private ModuleVersionIdentifier getProjectCoordinates(ResolvedVariantResult variant, ProjectComponentIdentifierInternal componentId) {
        Path identityPath = componentId.getIdentityPath();

        // TODO: Using the display name here is not great, however it is the same as the variant name.
        // Instead, the resolution result should expose the project coordinates via getExternalVariant.
        String variantName = variant.getDisplayName();
        ModuleVersionIdentifier coordinates = projectDependencyResolver.resolveVariant(ModuleVersionIdentifier.class, identityPath, variantName);

        if (coordinates == null) {
            throw new InvalidUserDataException(String.format(
                "Could not resolve coordinates for variant '%s' of project '%s'.",
                variantName, identityPath
            ));
        }

        return coordinates;
    }

    @Override
    @Nullable
    public ResolvedCoordinates resolveVariantCoordinates(ExternalDependency dependency, VariantWarningCollector warnings) {
        ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
        ModuleDependencyKey key = new ModuleDependencyKey(module, ModuleDependencyDetails.from(dependency, attributeDesugaring));

        ModuleVersionIdentifier resolved = mappings.get().resolvedModules.get(key);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }

        if (mappings.get().incompatibleModules.contains(key)) {
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
        return fallback.resolveComponentCoordinates(dependency);
    }

    @Override
    public ResolvedCoordinates resolveVariantCoordinates(ProjectDependency dependency, VariantWarningCollector warnings) {
        Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
        ProjectDependencyKey key = new ProjectDependencyKey(identityPath, ModuleDependencyDetails.from(dependency, attributeDesugaring));

        ModuleVersionIdentifier resolved = mappings.get().resolvedProjects.get(key);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }

        if (mappings.get().incompatibleProjects.contains(key)) {
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
        return fallback.resolveComponentCoordinates(dependency);
    }

    private static class ResolvedMappings {
        final Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules;
        final Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects;

        // Incompatible modules and projects are those that have multiple dependencies with the same
        // attributes and capabilities, but have somehow resolved to different coordinates. This can
        // often happen when the dependency is declared with a targetConfiguration.
        final Set<ModuleDependencyKey> incompatibleModules;
        final Set<ProjectDependencyKey> incompatibleProjects;

        ResolvedMappings(
            Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules,
            Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects,
            Set<ModuleDependencyKey> incompatibleModules,
            Set<ProjectDependencyKey> incompatibleProjects
        ) {
            this.resolvedModules = resolvedModules;
            this.resolvedProjects = resolvedProjects;
            this.incompatibleModules = incompatibleModules;
            this.incompatibleProjects = incompatibleProjects;
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
        final List<Capability> requestCapabilities;

        public ModuleDependencyDetails(
            AttributeContainer requestAttributes,
            List<Capability> requestCapabilities
        ) {
            this.requestAttributes = requestAttributes;
            this.requestCapabilities = requestCapabilities;
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
            return Objects.equals(requestAttributes, that.requestAttributes) && Objects.equals(requestCapabilities, that.requestCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestAttributes, requestCapabilities);
        }

        public static ModuleDependencyDetails from(ModuleDependency dependency, AttributeDesugaring attributeDesugaring) {
            ImmutableAttributes attributes = ((AttributeContainerInternal) dependency.getAttributes()).asImmutable();
            return new ModuleDependencyDetails(
                attributeDesugaring.desugar(attributes),
                dependency.getRequestedCapabilities()
            );
        }

        // Do not desugar here since resolution results already expose desugared attributes.
        public static ModuleDependencyDetails from(ComponentSelector componentSelector) {
            return new ModuleDependencyDetails(
                componentSelector.getAttributes(),
                componentSelector.getRequestedCapabilities()
            );
        }
    }
}
