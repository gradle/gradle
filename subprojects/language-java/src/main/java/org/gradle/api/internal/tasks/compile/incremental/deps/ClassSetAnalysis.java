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
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClassSetAnalysis {

    private final ClassSetAnalysisData classAnalysis;
    private final AnnotationProcessingData annotationProcessingData;
    private final ImmutableSetMultimap<String, String> classDependenciesFromAnnotationProcessing;
    private final ImmutableSetMultimap<String, GeneratedResource> resourceDependenciesFromAnnotationProcessing;

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis) {
        this(classAnalysis, new AnnotationProcessingData());
    }

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis, AnnotationProcessingData annotationProcessingData) {
        this.classAnalysis = classAnalysis;
        this.annotationProcessingData = annotationProcessingData;
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

    public ClassSetAnalysis withAnnotationProcessingData(AnnotationProcessingData annotationProcessingData) {
        return new ClassSetAnalysis(classAnalysis, annotationProcessingData);
    }

    public DependentsSet getRelevantDependents(Iterable<String> classes, IntSet constants) {
        final Set<String> accessibleResultClasses = Sets.newLinkedHashSet();
        final Set<String> privateResultClasses = Sets.newLinkedHashSet();
        final Set<GeneratedResource> resultResources = Sets.newLinkedHashSet();
        for (String cls : classes) {
            DependentsSet d = getRelevantDependents(cls, constants);
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

    public DependentsSet getRelevantDependents(String className, IntSet constants) {
        String fullRebuildCause = annotationProcessingData.getFullRebuildCause();
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        DependentsSet deps = getDependents(className);
        if (deps.isDependencyToAll()) {
            return deps;
        }
        if (!constants.isEmpty()) {
            return DependentsSet.dependencyToAll();
        }
        Set<String> classesDependingOnAllOthers = annotationProcessingData.participatesInClassGeneration(className) ? annotationProcessingData.getGeneratedTypesDependingOnAllOthers() : Collections.emptySet();
        Set<GeneratedResource> resourcesDependingOnAllOthers = annotationProcessingData.participatesInResourceGeneration(className) ? annotationProcessingData.getGeneratedResourcesDependingOnAllOthers() : Collections.emptySet();
        if (!deps.hasDependentClasses() && classesDependingOnAllOthers.isEmpty() && resourcesDependingOnAllOthers.isEmpty()) {
            return deps;
        }

        Set<String> privateResultClasses = new HashSet<String>();
        Set<String> accessibleResultClasses = new HashSet<String>();
        Set<GeneratedResource> resultResources = new HashSet<GeneratedResource>(resourcesDependingOnAllOthers);
        processDependentClasses(new HashSet<String>(), privateResultClasses, accessibleResultClasses, resultResources, deps.getPrivateDependentClasses(), deps.getAccessibleDependentClasses());
        processDependentClasses(new HashSet<String>(), privateResultClasses, accessibleResultClasses, resultResources, Collections.emptySet(), classesDependingOnAllOthers);
        accessibleResultClasses.remove(className);
        privateResultClasses.remove(className);

        return DependentsSet.dependents(privateResultClasses, accessibleResultClasses, resultResources);
    }

    public Set<String> getTypesToReprocess() {
        return annotationProcessingData.getAggregatedTypes();
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
        ImmutableSet<String> additionalClassDeps = classDependenciesFromAnnotationProcessing.get(className);
        ImmutableSet<GeneratedResource> additionalResourceDeps = resourceDependenciesFromAnnotationProcessing.get(className);
        if (additionalClassDeps.isEmpty() && additionalResourceDeps.isEmpty()) {
            return dependents;
        }
        return DependentsSet.dependents(dependents.getPrivateDependentClasses(), Sets.union(dependents.getAccessibleDependentClasses(), additionalClassDeps), Sets.union(dependents.getDependentResources(), additionalResourceDeps));
    }

    public IntSet getConstants(String className) {
        return classAnalysis.getConstants(className);
    }
}
