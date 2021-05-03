/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An extension of {@link ClassSetAnalysisData}, which can also handle annotation processing.
 * All logic for dealing with transitive dependencies is here, since annotation processing can affect it.
 * We should strive to merge the two into one class, to make the change detection algorithm easier to follow.
 */
public class ClassSetAnalysis {

    private final ClassSetAnalysisData classAnalysis;
    private final AnnotationProcessingData annotationProcessingData;
    private final CompilerApiData compilerApiData;
    private final ImmutableSetMultimap<String, String> classDependenciesFromAnnotationProcessing;
    private final ImmutableSetMultimap<String, GeneratedResource> resourceDependenciesFromAnnotationProcessing;

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis) {
        this(classAnalysis, new AnnotationProcessingData(), CompilerApiData.unavailableOf());
    }

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis, AnnotationProcessingData annotationProcessingData, CompilerApiData compilerApiData) {
        this.classAnalysis = classAnalysis;
        this.annotationProcessingData = annotationProcessingData;
        this.compilerApiData = compilerApiData;
        ImmutableSetMultimap.Builder<String, String> classDependenciesFromAnnotationProcessing = ImmutableSetMultimap.builder();
        for (Map.Entry<String, Set<String>> entry : annotationProcessingData.getGeneratedTypesByOrigin().entrySet()) {
            for (String generated : entry.getValue()) {
                String origin = entry.getKey();
                classDependenciesFromAnnotationProcessing.put(origin, generated);
                classDependenciesFromAnnotationProcessing.put(generated, origin);
            }
        }
        this.classDependenciesFromAnnotationProcessing = classDependenciesFromAnnotationProcessing.build();

        ImmutableSetMultimap.Builder<String, GeneratedResource> resourceDependenciesFromAnnotationProcessing = ImmutableSetMultimap.builder();
        for (Map.Entry<String, Set<GeneratedResource>> entry : annotationProcessingData.getGeneratedResourcesByOrigin().entrySet()) {
            for (GeneratedResource generated : entry.getValue()) {
                String origin = entry.getKey();
                resourceDependenciesFromAnnotationProcessing.put(origin, generated);
            }
        }
        this.resourceDependenciesFromAnnotationProcessing = resourceDependenciesFromAnnotationProcessing.build();
    }

    /**
     * Computes the types affected by the changes since some other class set, including transitively affected classes.
     */
    public ClassSetDiff findChangesSince(ClassSetAnalysis other) {
        DependentsSet directChanges = classAnalysis.getChangedClassesSince(other.classAnalysis);
        if (directChanges.isDependencyToAll()) {
            return new ClassSetDiff(directChanges, Collections.emptyMap());
        }
        DependentsSet transitiveChanges = other.findTransitiveDependents(directChanges.getAllDependentClasses(), Collections.emptyMap());
        if (transitiveChanges.isDependencyToAll()) {
            return new ClassSetDiff(transitiveChanges, Collections.emptyMap());
        }
        DependentsSet allChanges = DependentsSet.merge(Arrays.asList(directChanges, transitiveChanges));
        Map<String, IntSet> changedConstants = findChangedConstants(other, allChanges);
        return new ClassSetDiff(allChanges, changedConstants);
    }

    private Map<String, IntSet> findChangedConstants(ClassSetAnalysis other, DependentsSet affectedClasses) {
        if (affectedClasses.isDependencyToAll()) {
            return Collections.emptyMap();
        }
        Set<String> dependentClasses = affectedClasses.getAllDependentClasses();
        Map<String, IntSet> result = new HashMap<>(dependentClasses.size());
        for (String affectedClass : dependentClasses) {
            IntSet difference = new IntOpenHashSet(other.getConstants(affectedClass));
            difference.removeAll(getConstants(affectedClass));
            result.put(affectedClass, difference);
        }
        return result;
    }

    /**
     * An efficient version of {@link #findTransitiveDependents(String, IntSet)} when several classes have changed.
     */
    public DependentsSet findTransitiveDependents(Collection<String> classes, Map<String, IntSet> constants) {
        if (classes.isEmpty()) {
            return DependentsSet.empty();
        }
        final Set<String> accessibleResultClasses = new HashSet<>();
        final Set<String> privateResultClasses = new HashSet<>();
        final Set<GeneratedResource> resultResources = new HashSet<>();
        for (String cls : classes) {
            DependentsSet d = findTransitiveDependents(cls, constants.getOrDefault(cls, IntSets.emptySet()));
            if (d.isDependencyToAll()) {
                return d;
            }
            if (d.isEmpty()) {
                continue;
            }
            Set<String> accessibleDependentClasses = d.getAccessibleDependentClasses();
            Set<String> privateDependentClasses = d.getPrivateDependentClasses();
            Set<GeneratedResource> dependentResources = d.getDependentResources();

            accessibleResultClasses.addAll(accessibleDependentClasses);
            privateResultClasses.addAll(privateDependentClasses);
            resultResources.addAll(dependentResources);
        }
        return DependentsSet.dependents(privateResultClasses, accessibleResultClasses, resultResources);
    }

    /**
     * Computes the transitive dependents of a changed class.
     * If the class had any changes to inlineable constants, these need to be provided as the second parameter.
     */
    public DependentsSet findTransitiveDependents(String className, IntSet constants) {
        String fullRebuildCause = annotationProcessingData.getFullRebuildCause();
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }

        DependentsSet deps = getDependents(className);
        if (deps.isDependencyToAll()) {
            return deps;
        }
        if (!constants.isEmpty() && !compilerApiData.isAvailable()) {
            return DependentsSet.dependencyToAll("an inlineable constant in '" + className + "' has changed");
        }
        Set<String> classesDependingOnAllOthers = annotationProcessingData.participatesInClassGeneration(className) ? annotationProcessingData.getGeneratedTypesDependingOnAllOthers() : Collections.emptySet();
        Set<GeneratedResource> resourcesDependingOnAllOthers = annotationProcessingData.participatesInResourceGeneration(className) ? annotationProcessingData.getGeneratedResourcesDependingOnAllOthers() : Collections.emptySet();
        Set<String> accessibleConstantDependents = new ObjectOpenHashSet<>(accessibleConstantDependentsForClass(className));
        Set<String> privateConstantDependents = new ObjectOpenHashSet<>(privateConstantDependentsForClass(className));
        processTransitiveConstantDependentClasses(new ObjectOpenHashSet<>(Collections.singleton(className)), privateConstantDependents, accessibleConstantDependents);
        if (!deps.hasDependentClasses() && classesDependingOnAllOthers.isEmpty() && resourcesDependingOnAllOthers.isEmpty() && accessibleConstantDependents.isEmpty() && privateConstantDependents.isEmpty()) {
            return deps;
        }

        Set<String> dependents = new HashSet<>(deps.getAccessibleDependentClasses());

        Set<String> privateResultClasses = new HashSet<>();
        Set<String> accessibleResultClasses = new HashSet<>();
        Set<GeneratedResource> resultResources = new HashSet<>(resourcesDependingOnAllOthers);
        processDependentClasses(new HashSet<>(), privateResultClasses, accessibleResultClasses, resultResources, deps.getPrivateDependentClasses(), dependents);
        processDependentClasses(new HashSet<>(), privateResultClasses, accessibleResultClasses, resultResources, Collections.emptySet(), classesDependingOnAllOthers);
        processDependentClasses(new HashSet<>(), privateResultClasses, accessibleResultClasses, resultResources, privateConstantDependents, accessibleConstantDependents);
        accessibleResultClasses.remove(className);
        privateResultClasses.remove(className);

        return DependentsSet.dependents(privateResultClasses, accessibleResultClasses, resultResources);
    }

    public Set<String> getTypesToReprocess() {
        // Because of https://github.com/gradle/gradle/issues/13767 and
        // https://github.com/gradle/gradle/issues/15009 it is possible
        // that the types to reprocess are actually generated types
        // so when we see a type to reprocess we need to track what
        // actually generated this type, not use it directly!
        return annotationProcessingData.getAggregatedTypes()
            .stream()
            .map(annotationProcessingData::getOriginOf)
            .collect(Collectors.toSet());
    }

    public boolean isDependencyToAll(String className) {
        return classAnalysis.getDependents(className).isDependencyToAll();
    }

    /**
     * Accumulate dependent classes and resources. Dependent classes discovered can themselves be used to query
     * further dependents, while resources are just data accumulated along the way. Recurses for classes that
     * are "publicly accessbile", i.e. classes that are not just used privately in a class.
     */
    private void processDependentClasses(Set<String> visitedClasses,
                                         Set<String> privateResultClasses,
                                         Set<String> accessibleResultClasses,
                                         Set<GeneratedResource> resultResources,
                                         Iterable<String> privateDependentClasses,
                                         Iterable<String> accessibleDependentClasses) {

        for (String privateDependentClass : privateDependentClasses) {
            if (!visitedClasses.add(privateDependentClass)) {
                continue;
            }
            privateResultClasses.add(privateDependentClass);
            DependentsSet currentDependents = getDependents(privateDependentClass);
            if (!currentDependents.isDependencyToAll()) {
                resultResources.addAll(currentDependents.getDependentResources());
            }
        }

        processTransitiveDependentClasses(visitedClasses, accessibleResultClasses, resultResources, accessibleDependentClasses);
    }

    private void processTransitiveDependentClasses(Set<String> visitedClasses,
                                                   Set<String> accessibleResultClasses,
                                                   Set<GeneratedResource> resultResources,
                                                   Iterable<String> accessibleDependentClasses) {
        Deque<String> remainingAccessibleDependentClasses = new ArrayDeque<>();
        for (String accessibleDependentClass : accessibleDependentClasses) {
            remainingAccessibleDependentClasses.add(accessibleDependentClass);
        }

        while (!remainingAccessibleDependentClasses.isEmpty()) {
            String accessibleDependentClass = remainingAccessibleDependentClasses.pop();
            if (!visitedClasses.add(accessibleDependentClass)) {
                continue;
            }
            accessibleResultClasses.add(accessibleDependentClass);
            DependentsSet currentDependents = getDependents(accessibleDependentClass);
            if (!currentDependents.isDependencyToAll()) {
                resultResources.addAll(currentDependents.getDependentResources());
                remainingAccessibleDependentClasses.addAll(currentDependents.getAccessibleDependentClasses());
            }
        }
    }

    private DependentsSet getDependents(String className) {
        DependentsSet dependents = classAnalysis.getDependents(className);
        if (dependents.isDependencyToAll()) {
            return dependents;
        }
        ImmutableSet<String> annotationProcessingClassDeps = classDependenciesFromAnnotationProcessing.get(className);
        ImmutableSet<GeneratedResource> annotationProcessingResourceDeps = resourceDependenciesFromAnnotationProcessing.get(className);
        if (annotationProcessingClassDeps.isEmpty() && annotationProcessingResourceDeps.isEmpty()) {
            return dependents;
        }
        return DependentsSet.dependents(dependents.getPrivateDependentClasses(),
            Sets.union(dependents.getAccessibleDependentClasses(), annotationProcessingClassDeps),
            Sets.union(dependents.getDependentResources(), annotationProcessingResourceDeps)
        );
    }

    private void processTransitiveConstantDependentClasses(Set<Object> visited, Set<String> privateConstantDependents, Set<String> accessibleConstantDependents) {
        Deque<String> remainingAccessibleDependentClasses = new ArrayDeque<>(accessibleConstantDependents);
        while (!remainingAccessibleDependentClasses.isEmpty()) {
            String accessibleDependentClass = remainingAccessibleDependentClasses.pop();
            if (!visited.add(accessibleDependentClass)) {
                continue;
            }
            accessibleConstantDependents.add(accessibleDependentClass);
            privateConstantDependents.addAll(privateConstantDependentsForClass(accessibleDependentClass));
            remainingAccessibleDependentClasses.addAll(accessibleConstantDependentsForClass(accessibleDependentClass));
        }
    }

    private Set<String> accessibleConstantDependentsForClass(String constantOrigin) {
        if (compilerApiData.isAvailable()) {
            return compilerApiData.accessibleConstantDependentsForClass(constantOrigin);
        }
        return Collections.emptySet();
    }

    private Set<String> privateConstantDependentsForClass(String constantOrigin) {
        if (compilerApiData.isAvailable()) {
            return compilerApiData.privateConstantDependentsForClass(constantOrigin);
        }
        return Collections.emptySet();
    }

    public IntSet getConstants(String className) {
        return classAnalysis.getConstants(className);
    }

    /**
     * Provides the difference between two class sets, including which types are affected and which constants have changed.
     */
    public static final class ClassSetDiff {
        private final DependentsSet dependents;
        private final Map<String, IntSet> constants;

        public ClassSetDiff(DependentsSet dependents, Map<String, IntSet> constants) {
            this.dependents = dependents;
            this.constants = constants;
        }

        public DependentsSet getDependents() {
            return dependents;
        }

        public Map<String, IntSet> getConstants() {
            return constants;
        }
    }
}
