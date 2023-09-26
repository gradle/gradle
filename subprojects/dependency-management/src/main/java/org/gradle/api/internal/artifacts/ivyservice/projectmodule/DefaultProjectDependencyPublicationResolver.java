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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A service that will resolve a project identity path into publication coordinates. If the target variant
 * of the project is provided, this resolver will attempt to resolve the coordinates for that variant
 * specifically. Otherwise, it will attempt to resolve the coordinates of the project's root component as
 * long as the root component does not span across multiple coordinates.
 */
@ServiceScope(Scopes.Build.class)
public class DefaultProjectDependencyPublicationResolver implements ProjectDependencyPublicationResolver {
    private final ProjectPublicationRegistry publicationRegistry;
    private final ProjectConfigurer projectConfigurer;
    private final ProjectStateRegistry projects;

    private final VariantCoordinateResolverCache resolverCache = new VariantCoordinateResolverCache();

    public DefaultProjectDependencyPublicationResolver(ProjectPublicationRegistry publicationRegistry, ProjectConfigurer projectConfigurer, ProjectStateRegistry projects) {
        this.publicationRegistry = publicationRegistry;
        this.projectConfigurer = projectConfigurer;
        this.projects = projects;
    }

    @Override
    public <T> T resolveComponent(Class<T> coordsType, Path identityPath) {
        return withResolver(coordsType, identityPath,
            VariantCoordinateResolver::getComponentCoordinates
        );
    }

    @Nullable
    @Override
    public <T> T resolveVariant(Class<T> coordsType, Path identityPath, String variantName) {
        return withResolver(coordsType, identityPath, resolver ->
            resolver.getVariantCoordinates(variantName)
        );
    }

    @Nullable
    @Override
    public <T> T resolveVariantWithAttributeMatching(
        Class<T> coordsType,
        Path identityPath,
        ImmutableAttributes attributes,
        Collection<? extends Capability> capabilities,
        AttributesSchemaInternal consumerSchema
    ) {
        return withResolver(coordsType, identityPath, resolver ->
            resolver.getVariantCoordinatesWithAttributeMatching(
                attributes,
                capabilities,
                consumerSchema
            )
        );
    }

    /**
     * Execute the action with a resolver for the given project.
     */
    private <T> T withResolver(
        Class<T> coordsType,
        Path identityPath,
        Function<VariantCoordinateResolver<T>, T> action
    ) {
        ProjectState projectState = projects.stateFor(identityPath);

        // Ensure target project is configured
        projectConfigurer.configureFully(projectState);

        return projectState.fromMutableState(project -> {
            VariantCoordinateResolver<T> resolver = resolverCache.computeIfAbsent(identityPath, coordsType, key ->
                createResolver(identityPath, coordsType, project)
            );
            return action.apply(resolver);
        });
    }

    // It would be nice to get rid of the project parameter
    private <T> VariantCoordinateResolver<T> createResolver(Path identityPath, Class<T> coordsType, ProjectInternal project) {
        Map<ProjectComponentPublication, T> publications = getPublications(identityPath, coordsType);

        if (publications.isEmpty()) {
            return VariantCoordinateResolver.of(getImplicitCoordinates(coordsType, project));
        }

        // For all published components, find those that are not children of other components.
        // These are the top-level publications.
        Set<ProjectComponentPublication> topLevelWithComponent = new LinkedHashSet<>();
        Set<ProjectComponentPublication> topLevel = new LinkedHashSet<>();
        Set<SoftwareComponent> childComponents = getChildComponents(publications.keySet());
        for (Map.Entry<ProjectComponentPublication, T> entry : publications.entrySet()) {
            ProjectComponentPublication publication = entry.getKey();
            SoftwareComponent component = publication.getComponent().getOrNull();
            if (!publication.isAlias() && !childComponents.contains(component)) {
                topLevel.add(publication);
                if (component != null) {
                    topLevelWithComponent.add(publication);
                }
            }
        }

        if (topLevelWithComponent.size() == 1) {
            SoftwareComponentInternal singleComponent = topLevelWithComponent.iterator().next().getComponent().get();
            Map<SoftwareComponent, T> componentCoordinates = getComponentCoordinates(coordsType, publications.keySet());
            AttributesSchemaInternal producerSchema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
            return new MultiCoordinateVariantResolver<>(singleComponent, identityPath, componentCoordinates, producerSchema);
        }

        // See if all entry points have the same identifier
        return VariantCoordinateResolver.of(getCommonCoordinates(project, coordsType, topLevel));
    }

    /**
     * Get the coordinates of a project that has no publications.
     */
    private static <T> T getImplicitCoordinates(Class<T> coordsType, Project project) {
        // Project has no publications: simply use the project name in place of the dependency name
        if (coordsType.isAssignableFrom(ModuleVersionIdentifier.class)) {

            // TODO: Deprecate this behavior
//        String message = "Cannot publish dependency on " + project.getDisplayName() + " since it does not declare any publications. " +
//            "Publishing a component that depends on another project without publications";
//        DeprecationLogger.deprecate(message)
//            .withAdvice("Ensure " + project.getDisplayName() + " declares at least one publication.")
//            .willBecomeAnErrorInGradle9()
//            .withUpgradeGuideSection(8, "publishing_dependency_on_unpublished_project")
//            .nagUser();

            return coordsType.cast(DefaultModuleVersionIdentifier.newId(project.getGroup().toString(), project.getName(), project.getVersion().toString()));
        }

        throw new UnsupportedOperationException(String.format("Could not find any publications of type %s in %s.", coordsType.getSimpleName(), project.getDisplayName()));
    }

    /**
     * Try to find a single set of coordinates shared by all top-level publications.
     */
    private static <T> T getCommonCoordinates(Project project, Class<T> coordsType, Collection<ProjectComponentPublication> topLevel) {
        Iterator<ProjectComponentPublication> iterator = topLevel.iterator();
        T candidate = iterator.next().getCoordinates(coordsType);
        while (iterator.hasNext()) {
            T alternative = iterator.next().getCoordinates(coordsType);
            if (!candidate.equals(alternative)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.");
                formatter.node("Found the following publications in " + project.getDisplayName());
                formatter.startChildren();
                for (ProjectComponentPublication publication : topLevel) {
                    formatter.node(publication.getDisplayName().getCapitalizedDisplayName() + " with coordinates " + publication.getCoordinates(coordsType));
                }
                formatter.endChildren();
                throw new UnsupportedOperationException(formatter.toString());
            }
        }
        return candidate;
    }

    /**
     * For each declared component in a set of publications, map it with its coordinates.
     */
    private static <T> Map<SoftwareComponent, T> getComponentCoordinates(Class<T> coordsType, Collection<ProjectComponentPublication> publications) {
        Map<SoftwareComponent, T> coordinatesMap = new HashMap<>();
        for (ProjectComponentPublication publication : publications) {
            SoftwareComponent component = publication.getComponent().getOrNull();
            if (component != null && !publication.isAlias()) {
                T coordinates = publication.getCoordinates(coordsType);
                if (coordinates != null) {
                    coordinatesMap.put(component, coordinates);
                }
            }
        }
        return coordinatesMap;
    }

    /**
     * Given a project and a coordinate type, find all publications that publish a component
     * with the given coordinate type.
     */
    private <T> Map<ProjectComponentPublication, T> getPublications(Path identityPath, Class<T> coordsType) {
        Collection<ProjectComponentPublication> allPublications = publicationRegistry.getPublications(ProjectComponentPublication.class, identityPath);
        Map<ProjectComponentPublication, T> publications = new LinkedHashMap<>(allPublications.size());
        for (ProjectComponentPublication publication : allPublications) {
            T coordinates = publication.getCoordinates(coordsType);
            if (!publication.isLegacy() && coordinates != null) {
                publications.put(publication, coordinates);
            }
        }
        return publications;
    }

    /**
     * Get all components that are a child of another component.
     */
    private static Set<SoftwareComponent> getChildComponents(Collection<ProjectComponentPublication> publications) {
        Set<SoftwareComponent> children = new HashSet<>();
        for (ProjectComponentPublication publication : publications) {
            SoftwareComponent component = publication.getComponent().getOrNull();
            if (component instanceof ComponentWithVariants) {
                ComponentWithVariants parent = (ComponentWithVariants) component;
                // Child components are not top-level entry points.
                children.addAll(parent.getVariants());
            }
        }
        return children;
    }

    /**
     * Resolves the coordinates of variants of a single component
     */
    private interface VariantCoordinateResolver<T> {

        /**
         * Get the coordinates of the root component
         */
        T getComponentCoordinates();

        /**
         * Get the coordinates of the variant with given name
         */
        @Nullable
        T getVariantCoordinates(String variantName);

        @Nullable
        T getVariantCoordinatesWithAttributeMatching(
            ImmutableAttributes attributes,
            Collection<? extends Capability> capabilities,
            AttributesSchemaInternal consumerSchema
        );

        /**
         * Create a resolver that always returns the given coordinates
         */
        static <T> VariantCoordinateResolver<T> of(T coordinates) {
            return new FixedVariantCoordinateResolver<>(coordinates);
        }

        class FixedVariantCoordinateResolver<T> implements VariantCoordinateResolver<T> {
            private final T coordinates;

            private FixedVariantCoordinateResolver(T coordinates) {
                this.coordinates = coordinates;
            }

            @Override
            public T getComponentCoordinates() {
                return coordinates;
            }

            @Override
            public T getVariantCoordinates(String resolvedVariant) {
                return coordinates;
            }

            @Override
            public T getVariantCoordinatesWithAttributeMatching(
                ImmutableAttributes attributes,
                Collection<? extends Capability> capabilities,
                AttributesSchemaInternal consumerSchema
            ) {
                return coordinates;
            }
        }
    }

    /**
     * A {@link VariantCoordinateResolver} that supports composite components distributed across multiple coordinates
     */
    private static class MultiCoordinateVariantResolver<T> implements VariantCoordinateResolver<T> {
        private final SoftwareComponent root;
        private final Map<SoftwareComponent, T> componentCoordinates;
        private final AttributesSchemaInternal producerSchema;

        private final Lazy<ComponentWalkResult<T>> variants;

        private MultiCoordinateVariantResolver(
            SoftwareComponent root,
            Path identityPath,
            Map<SoftwareComponent, T> componentCoordinates,
            AttributesSchemaInternal producerSchema
        ) {
            this.root = root;
            this.componentCoordinates = componentCoordinates;
            this.producerSchema = producerSchema;
            this.variants = Lazy.locking().of(() -> walkRoot(root, componentCoordinates, identityPath));
        }

        public T getComponentCoordinates() {
            return componentCoordinates.get(root);
        }

        @Nullable
        public T getVariantCoordinates(String resolvedVariant) {
            return variants.get().nameToCoordinates.get(resolvedVariant);
        }

        @Nullable
        public T getVariantCoordinatesWithAttributeMatching(
            ImmutableAttributes attributes,
            Collection<? extends Capability> capabilities,
            AttributesSchemaInternal consumerSchema
        ) {
            ModuleIdentifier producerModule;
            T coordinates = getComponentCoordinates();
            if (coordinates instanceof ModuleVersionIdentifier) {
                producerModule = ((ModuleVersionIdentifier) coordinates).getModule();
            } else {
                throw new UnsupportedOperationException("Cannot variant match against a non-module component");
            }

            List<SoftwareComponentVariant> candidates = variants.get().allVariants;
            SoftwareComponentVariant match = AttributeSoftwareComponentVariantSelector.selectVariantsUsingAttributeMatching(
                candidates, attributes, capabilities, producerSchema, consumerSchema, producerModule
            );

            if (match == null) {
                return null;
            }

            return getVariantCoordinates(match.getName());
        }

        private static <T>  ComponentWalkResult<T> walkRoot(SoftwareComponent root, Map<SoftwareComponent, T> componentsMap, Path identityPath) {
            Map<String, T> map = new HashMap<>();
            List<SoftwareComponentVariant> variants = new ArrayList<>();
            ComponentWalker.walkComponent(root, componentsMap, (variant, coordinates) -> {
                T existing = map.put(variant.getName(), coordinates);
                if (existing != null) {
                    throw new InvalidUserDataException(String.format(
                        "Found multiple variants with name '%s' in component '%s' of project '%s'",
                        variant.getName(), root.getName(), identityPath
                    ));
                }
                variants.add(variant);
            });
            return new ComponentWalkResult<T>(map, variants);
        }

        static class ComponentWalkResult<T> {

            public final Map<String, T> nameToCoordinates;
            public final List<SoftwareComponentVariant> allVariants;

            public ComponentWalkResult(
                Map<String, T> nameToCoordinates,
                List<SoftwareComponentVariant> allVariants
            ) {
                this.nameToCoordinates = nameToCoordinates;
                this.allVariants = allVariants;
            }
        }
    }

    /**
     * Walks a composite component and its subcomponents to determine coordinates of each variant.
     */
    private static class ComponentWalker {

        interface ComponentVisitor<T> {
            void visitVariant(SoftwareComponentVariant variant, T coordinates);
        }

        /**
         * Visit every variant of a composite component
         */
        public static <T> void walkComponent(SoftwareComponent component, Map<SoftwareComponent, T> componentsMap, ComponentVisitor<T> visitor) {
            walkComponent(component, componentsMap, new HashSet<>(), new HashSet<>(), visitor);
        }

        private static <T> void walkComponent(
            SoftwareComponent component,
            Map<SoftwareComponent, T> componentCoordinates,
            Set<SoftwareComponent> componentsSeen,
            Set<T> coordinatesSeen,
            ComponentVisitor<T> visitor
        ) {
            if (!componentsSeen.add(component)) {
                throw new InvalidUserDataException("Circular dependency detected while resolving component coordinates.");
            }

            T coordinates = componentCoordinates.get(component);
            if (!coordinatesSeen.add(coordinates)) {
                throw new InvalidUserDataException("Multiple child components may not share the same coordinates.");
            }

            // First visit the local variants
            if (component instanceof SoftwareComponentInternal) {
                SoftwareComponentInternal componentInternal = (SoftwareComponentInternal) component;
                for (SoftwareComponentVariant variant : componentInternal.getUsages()) {
                    visitor.visitVariant(variant, coordinates);
                }
            }

            // Then visit all child components' variants
            if (component instanceof ComponentWithVariants) {
                ComponentWithVariants parent = (ComponentWithVariants) component;
                for (SoftwareComponent child : parent.getVariants()) {
                    walkComponent(child, componentCoordinates, componentsSeen, coordinatesSeen, visitor);
                }
            }
        }
    }

    private static class VariantCoordinateResolverCache {
        private final Map<Key, VariantCoordinateResolver<?>> cache = new ConcurrentHashMap<>();

        public <T> VariantCoordinateResolver<T> computeIfAbsent(Path identityPath, Class<T> coordsType, Function<Key, VariantCoordinateResolver<T>> factory) {
            Key key = new Key(identityPath, coordsType);
            VariantCoordinateResolver<?> result = cache.computeIfAbsent(key, factory);
            return Cast.uncheckedCast(result);
        }

        private static class Key {
            private final Path identityPath;
            private final Class<?> coordsType;

            public Key(Path identityPath, Class<?> coordsType) {
                this.identityPath = identityPath;
                this.coordsType = coordsType;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Key key = (Key) o;
                return Objects.equals(identityPath, key.identityPath) && Objects.equals(coordsType, key.coordsType);
            }

            @Override
            public int hashCode() {
                return identityPath.hashCode() ^ coordsType.hashCode();
            }
        }
    }


    /**
     * A simplified re-implementation of variant-aware matching, adapted to work for publishing types
     * like {@link SoftwareComponentVariant} instead of internal dependency-management types.
     *
     * Eventually, we should consider merging this with the original implementation,
     * {@link org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector}.
     */
    private static class AttributeSoftwareComponentVariantSelector {

        private static final AttributeMatchingExplanationBuilder EXPLANATION_BUILDER = AttributeMatchingExplanationBuilder.logging();

        @Nullable
        public static SoftwareComponentVariant selectVariantsUsingAttributeMatching(
            List<SoftwareComponentVariant> candidates,
            ImmutableAttributes attributes,
            Collection<? extends Capability> capabilities,
            AttributesSchemaInternal producerSchema,
            AttributesSchemaInternal consumerSchema,
            ModuleIdentifier componentModule
        ) {
            AttributeMatcher attributeMatcher = consumerSchema.withProducer(producerSchema);
            ImmutableList<SoftwareComponentVariant> variantsProvidingRequestedCapabilities = filterVariantsByRequestedCapabilities(componentModule, capabilities, candidates, true);
            if (variantsProvidingRequestedCapabilities.isEmpty()) {
                return null;
            }

            List<SoftwareComponentVariant> matches = attributeMatcher.matches(variantsProvidingRequestedCapabilities, attributes, EXPLANATION_BUILDER);
            if (matches.size() == 1) {
                return matches.get(0);
            }

            // there's an ambiguity, but we may have several variants matching the requested capabilities.
            // Here we're going to check if in the candidates, there's a single one _strictly_ matching the requested capabilities.
            List<SoftwareComponentVariant> strictlyMatchingCapabilities = filterVariantsByRequestedCapabilities(componentModule, capabilities, matches, false);
            if (strictlyMatchingCapabilities.size() == 1) {
                return strictlyMatchingCapabilities.get(0);
            } else if (strictlyMatchingCapabilities.size() > 1) {
                // there are still more than one candidate, but this time we know only a subset strictly matches the required attributes
                // so we perform another round of selection on the remaining candidates
                strictlyMatchingCapabilities = attributeMatcher.matches(strictlyMatchingCapabilities, attributes, EXPLANATION_BUILDER);
                if (strictlyMatchingCapabilities.size() == 1) {
                    return strictlyMatchingCapabilities.get(0);
                }
            }

            return null;
        }
    }

    private static ImmutableList<SoftwareComponentVariant> filterVariantsByRequestedCapabilities(ModuleIdentifier componentModule, Collection<? extends Capability> explicitRequestedCapabilities, Collection<? extends SoftwareComponentVariant> consumableVariants, boolean lenient) {
        if (consumableVariants.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<SoftwareComponentVariant> builder = ImmutableList.builderWithExpectedSize(consumableVariants.size());
        boolean explicitlyRequested = !explicitRequestedCapabilities.isEmpty();
        for (SoftwareComponentVariant variant : consumableVariants) {
            Set<? extends Capability> capabilities = variant.getCapabilities();
            MatchResult result;
            if (explicitlyRequested) {
                // some capabilities are explicitly required (in other words, we're not _necessarily_ looking for the default capability
                // so we need to filter the configurations
                result = providesAllCapabilities(componentModule, explicitRequestedCapabilities, capabilities);
            } else {
                // we need to make sure the variants we consider provide the implicit capability
                result = containsImplicitCapability(capabilities, componentModule.getGroup(), componentModule.getName());
            }
            if (result.matches) {
                if (lenient || result == MatchResult.EXACT_MATCH) {
                    builder.add(variant);
                }
            }
        }
        return builder.build();
    }

    /**
     * Determines if a producer variant provides all the requested capabilities. When doing so it does
     * NOT consider capability versions, as they will be used later in the engine during conflict resolution.
     */
    private static MatchResult providesAllCapabilities(ModuleIdentifier componentModule, Collection<? extends Capability> explicitRequestedCapabilities, Set<? extends Capability> providerCapabilities) {
        if (providerCapabilities.isEmpty()) {
            // producer doesn't declare anything, so we assume that it only provides the implicit capability
            if (explicitRequestedCapabilities.size() == 1) {
                Capability requested = explicitRequestedCapabilities.iterator().next();
                if (requested.getGroup().equals(componentModule.getGroup()) && requested.getName().equals(componentModule.getName())) {
                    return MatchResult.EXACT_MATCH;
                }
            }
        }
        for (Capability requested : explicitRequestedCapabilities) {
            String requestedGroup = requested.getGroup();
            String requestedName = requested.getName();
            boolean found = false;
            for (Capability provided : providerCapabilities) {
                if (provided.getGroup().equals(requestedGroup) && provided.getName().equals(requestedName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return MatchResult.NO_MATCH;
            }
        }
        boolean exactMatch = explicitRequestedCapabilities.size() == providerCapabilities.size();
        return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
    }

    private static MatchResult containsImplicitCapability(Collection<? extends Capability> capabilities, String group, String name) {
        if (capabilities.isEmpty()) {
            // An empty capability list means that it's an implicit capability only
            return MatchResult.EXACT_MATCH;
        }
        for (Capability capability : capabilities) {
            capability = unwrap(capability);
            if (group.equals(capability.getGroup()) && name.equals(capability.getName())) {
                boolean exactMatch = capabilities.size() == 1;
                return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
            }
        }
        return MatchResult.NO_MATCH;
    }

    private static Capability unwrap(Capability capability) {
        if (capability instanceof ShadowedCapability) {
            return ((ShadowedCapability) capability).getShadowedCapability();
        }
        return capability;
    }

    private enum MatchResult {
        NO_MATCH(false),
        MATCHES_ALL(true),
        EXACT_MATCH(true);

        private final boolean matches;

        MatchResult(boolean match) {
            this.matches = match;
        }
    }
}
