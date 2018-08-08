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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClassSetAnalysis {

    private final ClassSetAnalysisData classAnalysis;
    private final AnnotationProcessingData annotationProcessingData;
    private final ImmutableSetMultimap<String, String> dependenciesFromAnnotationProcessing;

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis) {
        this(classAnalysis, new AnnotationProcessingData());
    }

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis, AnnotationProcessingData annotationProcessingData) {
        this.classAnalysis = classAnalysis;
        this.annotationProcessingData = annotationProcessingData;
        ImmutableSetMultimap.Builder<String, String> dependenciesFromAnnotationProcessing = ImmutableSetMultimap.builder();
        for (Map.Entry<String, Set<String>> entry : annotationProcessingData.getGeneratedTypesByOrigin().entrySet()) {
            for (String generated : entry.getValue()) {
                String origin = entry.getKey();
                dependenciesFromAnnotationProcessing.put(origin, generated);
                dependenciesFromAnnotationProcessing.put(generated, origin);
            }
        }
        this.dependenciesFromAnnotationProcessing = dependenciesFromAnnotationProcessing.build();
    }

    public ClassSetAnalysis withAnnotationProcessingData(AnnotationProcessingData annotationProcessingData) {
        return new ClassSetAnalysis(classAnalysis, annotationProcessingData);
    }

    public DependentsSet getRelevantDependents(Iterable<String> classes, IntSet constants) {
        Set<String> result = null;
        for (String cls : classes) {
            DependentsSet d = getRelevantDependents(cls, constants);
            if (d.isDependencyToAll()) {
                return d;
            }
            Set<String> dependentClasses = d.getDependentClasses();
            if (dependentClasses.isEmpty()) {
                continue;
            }
            if (result == null) {
                result = Sets.newLinkedHashSet();
            }
            result.addAll(dependentClasses);
        }
        return result == null ? DependentsSet.empty() : DependentsSet.dependents(result);
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
        Set<String> dependingOnAllOthers = annotationProcessingData.getGeneratedTypesDependingOnAllOthers();
        if (deps.getDependentClasses().isEmpty() && dependingOnAllOthers.isEmpty()) {
            return deps;
        }
        Set<String> result = new HashSet<String>();
        recurseDependents(new HashSet<String>(), result, deps.getDependentClasses());
        recurseDependents(new HashSet<String>(), result, dependingOnAllOthers);
        result.remove(className);
        return DependentsSet.dependents(result);
    }

    public Set<String> getTypesToReprocess() {
        return annotationProcessingData.getAggregatedTypes();
    }

    public boolean isDependencyToAll(String className) {
        return classAnalysis.getDependents(className).isDependencyToAll();
    }

    private void recurseDependents(Set<String> visited, Set<String> result, Iterable<String> dependentClasses) {
        for (String d : dependentClasses) {
            if (!visited.add(d)) {
                continue;
            }
            if (!isNestedClass(d)) {
                result.add(d);
            }
            DependentsSet currentDependents = getDependents(d);
            if (!currentDependents.isDependencyToAll()) {
                recurseDependents(visited, result, currentDependents.getDependentClasses());
            }
        }
    }

    private DependentsSet getDependents(String className) {
        DependentsSet dependents = classAnalysis.getDependents(className);
        if (dependents.isDependencyToAll()) {
            return dependents;
        }
        ImmutableSet<String> additionalDeps = dependenciesFromAnnotationProcessing.get(className);
        if (additionalDeps.isEmpty()) {
            return dependents;
        }
        return DependentsSet.dependents(Sets.union(dependents.getDependentClasses(), additionalDeps));
    }

    private boolean isNestedClass(String d) {
        return d.contains("$");
    }

    public IntSet getConstants(String className) {
        return classAnalysis.getConstants(className);
    }
}
