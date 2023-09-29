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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.Cast;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return withCoordinateResolver(coordsType, identityPath,
            VariantCoordinateResolver::getComponentCoordinates
        );
    }

    @Nullable
    @Override
    public <T> T resolveVariant(Class<T> coordsType, Path identityPath, String variantName) {
        return withCoordinateResolver(coordsType, identityPath, resolver ->
            resolver.getVariantCoordinates(variantName)
        );
    }

    /**
     * Execute the action with a resolver for the given project.
     */
    private <T> T withCoordinateResolver(Class<T> coordsType, Path identityPath, Function<VariantCoordinateResolver<T>, T> action) {
        ProjectState projectState = projects.stateFor(identityPath);

        // Ensure target project is configured
        projectConfigurer.configureFully(projectState);

        return projectState.fromMutableState(project -> {
            VariantCoordinateResolver<T> resolver = resolverCache.computeIfAbsent(identityPath, coordsType, key ->
                createCoordinateResolver(identityPath, coordsType, project)
            );
            return action.apply(resolver);
        });
    }

    // It would be nice to get rid of the project parameter
    private <T> VariantCoordinateResolver<T> createCoordinateResolver(Path identityPath, Class<T> coordsType, ProjectInternal project) {
        Map<ProjectComponentPublication, T> publications = getPublications(identityPath, coordsType);

        if (publications.isEmpty()) {
            return VariantCoordinateResolver.fixed(getImplicitCoordinates(coordsType, project));
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
            return new MultiCoordinateVariantResolver<>(singleComponent, identityPath, componentCoordinates);
        }

        // See if all entry points have the same identifier
        return VariantCoordinateResolver.fixed(getCommonCoordinates(project, coordsType, topLevel));
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

        /**
         * Create a resolver that always returns the given coordinates
         */
        static <T> VariantCoordinateResolver<T> fixed(T coordinates) {
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
        }
    }

    /**
     * A {@link VariantCoordinateResolver} that supports composite components distributed across multiple coordinates
     */
    private static class MultiCoordinateVariantResolver<T> implements VariantCoordinateResolver<T> {
        private final SoftwareComponent root;
        private final Map<SoftwareComponent, T> componentCoordinates;

        private final Lazy<Map<String, T>> variantCoordinatesMap;

        private MultiCoordinateVariantResolver(SoftwareComponent root, Path identityPath, Map<SoftwareComponent, T> componentCoordinates) {
            this.root = root;
            this.componentCoordinates = componentCoordinates;
            this.variantCoordinatesMap = Lazy.locking().of(() -> mapVariantNamesToCoordinates(root, componentCoordinates, identityPath));
        }

        public T getComponentCoordinates() {
            return componentCoordinates.get(root);
        }

        @Nullable
        public T getVariantCoordinates(String resolvedVariant) {
            return variantCoordinatesMap.get().get(resolvedVariant);
        }

        private static <T>  Map<String, T> mapVariantNamesToCoordinates(SoftwareComponent root, Map<SoftwareComponent, T> componentsMap, Path identityPath) {
            Map<String, T> result = new HashMap<>();
            ComponentWalker.walkComponent(root, componentsMap, (variant, coordinates) -> {
                if (result.put(variant.getName(), coordinates) != null) {
                    throw new InvalidUserDataException(String.format(
                        "Found multiple variants with name '%s' in component '%s' of project '%s'",
                        variant.getName(), root.getName(), identityPath
                    ));
                }
            });
            return result;
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
            walkComponent(component, componentsMap, new LinkedHashSet<>(), new HashSet<>(), visitor);
        }

        private static <T> void walkComponent(
            SoftwareComponent component,
            Map<SoftwareComponent, T> componentCoordinates,
            Set<SoftwareComponent> componentsSeen,
            Set<T> coordinatesSeen,
            ComponentVisitor<T> visitor
        ) {
            if (!componentsSeen.add(component)) {
                String allComponents = componentsSeen.stream()
                    .map(SoftwareComponent::getName)
                    .collect(Collectors.joining(", "));
                throw new InvalidUserDataException("Circular dependency detected while resolving component coordinates. Found the following components: " + allComponents);
            }

            T coordinates = componentCoordinates.get(component);
            if (!coordinatesSeen.add(coordinates)) {
                throw new InvalidUserDataException("Multiple child components may not share the same coordinates: " + coordinates);
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
}
