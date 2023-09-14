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
        return resolve(coordsType, identityPath,
            VariantCoordinateResolver::getComponentCoordinates
        );
    }

    @Override
    public <T> T resolveVariant(Class<T> coordsType, Path identityPath, String resolvedVariant) {
        return resolve(coordsType, identityPath, resolver ->
            resolver.getVariantCoordinates(resolvedVariant)
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
        return resolve(coordsType, identityPath, resolver ->
            resolver.getVariantCoordinatesWithAttributeMatching(
                attributes,
                capabilities,
                consumerSchema
            )
        );
    }

    private <T> T resolve(Class<T> coordsType, Path identityPath, Function<VariantCoordinateResolver<T>, T> action) {
        ProjectState projectState = projects.stateFor(identityPath);
        // Could probably apply some caching and some immutable types

        // Ensure target project is configured
        projectConfigurer.configureFully(projectState);

        return projectState.fromMutableState(project -> resolve(coordsType, identityPath, project, action));
    }

    private <T> T resolve(Class<T> coordsType, Path identityPath, ProjectInternal project, Function<VariantCoordinateResolver<T>, T> action) {
        VariantCoordinateResolver<T> resolver = resolverCache.get(coordsType, identityPath);
        if (resolver != null) {
            return action.apply(resolver);
        }

        List<ProjectComponentPublication> publications = new ArrayList<>();
        for (ProjectComponentPublication publication : publicationRegistry.getPublications(ProjectComponentPublication.class, identityPath)) {
            if (!publication.isLegacy() && publication.getCoordinates(coordsType) != null) {
                publications.add(publication);
            }
        }

        if (publications.isEmpty()) {

            // TODO: Deprecate this behavior
//            DeprecationLogger.deprecate("Publishing a project which depends on another project, while the target project has no publications")
//                .withAdvice("Ensure the target project declares at least one publication, do not publish the current project, or remove the dependency on the target project.")
//                .willBecomeAnErrorInGradle9()
//                .withUpgradeGuideSection(8, "publishing_dependency_on_unpublished_project")
//                .nagUser();

            // Project has no publications: simply use the project name in place of the dependency name
            if (coordsType.isAssignableFrom(ModuleVersionIdentifier.class)) {
                return coordsType.cast(DefaultModuleVersionIdentifier.newId(project.getGroup().toString(), project.getName(), project.getVersion().toString()));
            }
            throw new UnsupportedOperationException(String.format("Could not find any publications of type %s in %s.", coordsType.getSimpleName(), project.getDisplayName()));
        }

        // Select all entry points. An entry point is a publication that does not contain a component whose parent is also published
        Set<SoftwareComponent> children = getChildComponents(publications);
        Set<ProjectComponentPublication> topLevel = new LinkedHashSet<>();
        Set<ProjectComponentPublication> topLevelWithComponent = new LinkedHashSet<>();
        for (ProjectComponentPublication publication : publications) {
            SoftwareComponent component = publication.getComponent().getOrNull();
            if (!publication.isAlias() && !children.contains(component)) {
                topLevel.add(publication);
                if (component != null) {
                    topLevelWithComponent.add(publication);
                }
            }
        }

        if (topLevelWithComponent.size() == 1) {
            SoftwareComponentInternal singleComponent = topLevelWithComponent.iterator().next().getComponent().get();
            AttributesSchemaInternal schema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
            resolver = resolverCache.put(singleComponent, identityPath, coordsType, publications, schema);
            return action.apply(resolver);
        }

        // The project either has no component or multiple components. In either case, fall-back
        // to a returning a single common coordinate for all publications, if possible.
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

    private static class VariantCoordinateResolver<T> {
        private final SoftwareComponent root;
        private final Path identityPath;
        private final Map<SoftwareComponent, T> componentsMap;
        private final AttributesSchemaInternal producerSchema;

        private List<SoftwareComponentVariant> variants;
        private Map<String, T> variantCoordinatesMap;

        private VariantCoordinateResolver(SoftwareComponent root, Path identityPath, Map<SoftwareComponent, T> componentsMap, AttributesSchemaInternal producerSchema) {
            this.root = root;
            this.identityPath = identityPath;
            this.componentsMap = componentsMap;
            this.producerSchema = producerSchema;
        }

        public T getComponentCoordinates() {
            T coordinates = componentsMap.get(root);
            if (coordinates == null) {
                throw new UnsupportedOperationException();
            }
            return coordinates;
        }

        @Nullable
        public T getVariantCoordinates(@Nullable String resolvedVariant) {
            return getVariantCoordinates().get(resolvedVariant);
        }

        private void init() {
            if (variantCoordinatesMap != null && variants != null) {
                return;
            }

            variants = new ArrayList<>();
            variantCoordinatesMap = new HashMap<>();
            visitComponent(root, componentsMap, (variant, coordinates) -> {
                if (variantCoordinatesMap.containsKey(variant.getName())) {
                    throw new InvalidUserDataException("Found multiple variants with name '" + variant.getName() + "' in component '" + root.getName() + "' of project '" + identityPath + "'");
                }
                variants.add(variant);
                variantCoordinatesMap.put(variant.getName(), coordinates);
            });
        }

        private Map<String, T> getVariantCoordinates() {
            init();
            return variantCoordinatesMap;
        }

        private List<SoftwareComponentVariant> getVariantsForMatching() {
            init();
            return variants;
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

            List<SoftwareComponentVariant> candidates = getVariantsForMatching();
            SoftwareComponentVariant match = AttributeSoftwareComponentVariantSelector.selectVariantsUsingAttributeMatching(
                candidates, attributes, capabilities, producerSchema, consumerSchema, producerModule
            );

            if (match == null) {
                return null;
            }

            return getVariantCoordinates(match.getName());
        }

        interface ComponentVisitor<T> {
            void visitVariant(SoftwareComponentVariant variant, T coordinates);
        }

        private static <T> void visitComponent(SoftwareComponent component, Map<SoftwareComponent, T> componentsMap, ComponentVisitor<T> visitor) {
            visitComponent(component, componentsMap, new HashSet<>(), new HashSet<>(), visitor);
        }

        private static <T> void visitComponent(
            SoftwareComponent component,
            Map<SoftwareComponent, T> componentsMap,
            Set<SoftwareComponent> componentsSeen,
            Set<T> coordinatesSeen,
            ComponentVisitor<T> visitor
        ) {
            if (!componentsSeen.add(component)) {
                throw new InvalidUserDataException("Circular dependency detected while resolving component coordinates.");
            }

            T coordinates = componentsMap.get(component);
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
                    visitComponent(child, componentsMap, componentsSeen, coordinatesSeen, visitor);
                }
            }
        }
    }

    private static class VariantCoordinateResolverCache {
        private final Map<Key, VariantCoordinateResolver<?>> cache = new ConcurrentHashMap<>();

        @Nullable
        public <T> VariantCoordinateResolver<T> get(Class<T> coordsType, Path identityPath) {
            VariantCoordinateResolver<?> result = cache.get(new Key(identityPath, coordsType));
            return Cast.uncheckedCast(result);
        }

        public <T> VariantCoordinateResolver<T> put(SoftwareComponent root, Path identityPath, Class<T> coordsType, List<ProjectComponentPublication> publications, AttributesSchemaInternal schema) {
            Key key = new Key(identityPath, coordsType);
            assert !cache.containsKey(key);

            VariantCoordinateResolver<?> result = cache.computeIfAbsent(key, k -> createCoordinateResolver(root, identityPath, coordsType, publications, schema));
            return Cast.uncheckedCast(result);
        }

        private static <T> VariantCoordinateResolver<T> createCoordinateResolver(SoftwareComponent root, Path identityPath, Class<T> coordsType, List<ProjectComponentPublication> publications, AttributesSchemaInternal schema) {
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

            return new VariantCoordinateResolver<>(root, identityPath, coordinatesMap, schema);
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
                return Objects.hash(identityPath, coordsType);
            }
        }
    }

    private static Set<SoftwareComponent> getChildComponents(List<ProjectComponentPublication> publications) {
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
