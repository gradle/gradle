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
        Set<String> resultClasses = null;
        Set<GeneratedResource> resultResources = null;
        for (String cls : classes) {
            DependentsSet d = getRelevantDependents(cls, constants);
            if (d.isDependencyToAll()) {
                return d;
            }
            Set<String> dependentClasses = d.getDependentClasses();
            Set<GeneratedResource> dependentResources = d.getDependentResources();
            if (dependentClasses.isEmpty() && dependentResources.isEmpty()) {
                continue;
            }
            if (resultClasses == null) {
                resultClasses = Sets.newLinkedHashSet();
            }
            resultClasses.addAll(dependentClasses);
            if (resultResources == null) {
                resultResources = Sets.newLinkedHashSet();
            }
            resultResources.addAll(dependentResources);
        }
        return resultClasses == null ? DependentsSet.empty() : DependentsSet.dependents(resultClasses, resultResources);
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
        Set<String> classesDependingOnAllOthers = annotationProcessingData.getGeneratedTypesDependingOnAllOthers();
        Set<GeneratedResource> resourcesDependingOnAllOthers = annotationProcessingData.getGeneratedResourcesDependingOnAllOthers();
        if (deps.getDependentClasses().isEmpty() && classesDependingOnAllOthers.isEmpty() && resourcesDependingOnAllOthers.isEmpty()) {
            return deps;
        }

        Set<String> resultClasses = new HashSet<String>();
        Set<GeneratedResource> resultResources = new HashSet<GeneratedResource>(resourcesDependingOnAllOthers);
        recurseDependentClasses(new HashSet<String>(), resultClasses, resultResources, deps.getDependentClasses());
        recurseDependentClasses(new HashSet<String>(), resultClasses, resultResources, classesDependingOnAllOthers);
        resultClasses.remove(className);

        return DependentsSet.dependents(resultClasses, resultResources);
    }

    public Set<String> getTypesToReprocess() {
        return annotationProcessingData.getAggregatedTypes();
    }

    public boolean isDependencyToAll(String className) {
        return classAnalysis.getDependents(className).isDependencyToAll();
    }

    /**
     * Recursively accumulate dependent classes and resources.  Dependent classes discovered can themselves be used to query
     * further dependents, while resources are just data accumulated along the way.
     */
    private void recurseDependentClasses(Set<String> visitedClasses, Set<String> resultClasses, Set<GeneratedResource> resultResources, Iterable<String> dependentClasses) {
        for (String d : dependentClasses) {
            if (!visitedClasses.add(d)) {
                continue;
            }
            if (!isNestedClass(d)) {
                resultClasses.add(d);
            }
            DependentsSet currentDependents = getDependents(d);
            if (!currentDependents.isDependencyToAll()) {
                resultResources.addAll(currentDependents.getDependentResources());
                recurseDependentClasses(visitedClasses, resultClasses, resultResources, currentDependents.getDependentClasses());
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
        return DependentsSet.dependents(Sets.union(dependents.getDependentClasses(), additionalClassDeps), Sets.union(dependents.getDependentResources(), additionalResourceDeps));
    }

    private boolean isNestedClass(String d) {
        return d.contains("$");
    }

    public IntSet getConstants(String className) {
        return classAnalysis.getConstants(className);
    }
}
