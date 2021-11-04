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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Combines {@link ClassSetAnalysisData}, {@link AnnotationProcessingData} and {@link CompilerApiData} to implement the transitive change detection algorithm.
 */
public class ClassSetAnalysis {

    private final ClassSetAnalysisData classAnalysis;
    private final AnnotationProcessingData annotationProcessingData;
    private final CompilerApiData compilerApiData;

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis) {
        this(classAnalysis, new AnnotationProcessingData(), CompilerApiData.unavailable());
    }

    public ClassSetAnalysis(ClassSetAnalysisData classAnalysis, AnnotationProcessingData annotationProcessingData, CompilerApiData compilerApiData) {
        this.classAnalysis = classAnalysis;
        this.annotationProcessingData = annotationProcessingData;
        this.compilerApiData = compilerApiData;
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
     * Computes the transitive dependents of a set of changed classes. If the classes had any changes to inlineable constants, these need to be provided as the second parameter.
     *
     * If incremental annotation processing encountered issues in the previous compilation, a full recompilation is required.
     * If any inlineable constants have changed and the compiler does not support exact constant dependency tracking, then a full recompilation is required.
     * Otherwise follows the below rules for all of the given classes, as well as the classes that were marked as "always recompile" by annotation processing:
     *
     * Starts at this class and capture all classes that reference this class and all classes and resources that were generated from this class.
     * Then does the same analysis for all classes that expose this class on their ABI recursively until no more new classes are discovered.
     */
    public DependentsSet findTransitiveDependents(Collection<String> classes, Map<String, IntSet> changedConstantsByClass) {
        if (classes.isEmpty()) {
            return DependentsSet.empty();
        }
        String fullRebuildCause = annotationProcessingData.getFullRebuildCause();
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        if (!compilerApiData.isSupportsConstantsMapping()) {
            for (Map.Entry<String, IntSet> changedConstantsOfClass : changedConstantsByClass.entrySet()) {
                if (!changedConstantsOfClass.getValue().isEmpty()) {
                    return DependentsSet.dependencyToAll("an inlineable constant in '" + changedConstantsOfClass.getKey() + "' has changed");
                }
            }
        }
        Set<String> privateDependents = new HashSet<>();
        Set<String> accessibleDependents = new HashSet<>();
        Set<GeneratedResource> dependentResources = new HashSet<>(annotationProcessingData.getGeneratedResourcesDependingOnAllOthers());
        Set<String> visited = new HashSet<>();
        Deque<String> remaining = new ArrayDeque<>(classes);
        remaining.addAll(annotationProcessingData.getGeneratedTypesDependingOnAllOthers());

        while (!remaining.isEmpty()) {
            String current = remaining.pop();
            if (!visited.add(current)) {
                continue;
            }
            accessibleDependents.add(current);
            DependentsSet dependents = findDirectDependents(current);
            if (dependents.isDependencyToAll()) {
                return dependents;
            }
            dependentResources.addAll(dependents.getDependentResources());
            privateDependents.addAll(dependents.getPrivateDependentClasses());
            remaining.addAll(dependents.getAccessibleDependentClasses());
        }

        privateDependents.removeAll(classes);
        accessibleDependents.removeAll(classes);
        return DependentsSet.dependents(privateDependents, accessibleDependents, dependentResources);
    }

    /**
     * Finds all the classes and resources that are directly affected by the given one. This includes:
     *
     * - Classes that referenced this class in their bytecode
     * - Classes that use a constant declared in this class
     * - Classes and resources that were generated from this class
     */
    private DependentsSet findDirectDependents(String className) {
        Set<String> generatedClasses = annotationProcessingData.getGeneratedTypesByOrigin().getOrDefault(className, Collections.emptySet());
        Set<GeneratedResource> generatedResources = annotationProcessingData.getGeneratedResourcesByOrigin().getOrDefault(className, Collections.emptySet());
        DependentsSet generatedDeps = DependentsSet.dependents(Collections.emptySet(), generatedClasses, generatedResources);
        return DependentsSet.merge(Arrays.asList(classAnalysis.getDependents(className), compilerApiData.getConstantDependentsForClass(className), generatedDeps));
    }

    /**
     * Returns the types that need to be reprocessed based on which classes are due to be recompiled. This includes:
     *
     * - types which are annotated with aggregating annotations, as aggregating processors need to see them regardless of what has changed
     * - the originating types of generated classes that need to be recompiled, since they wouldn't exist if the originating type is not reprocessed
     */
    public Set<String> getTypesToReprocess(Set<String> compiledClasses) {
        Set<String> typesToReprocess = new HashSet<>(annotationProcessingData.getAggregatedTypes());
        for (Map.Entry<String, Set<String>> entry : annotationProcessingData.getGeneratedTypesByOrigin().entrySet()) {
            if (entry.getValue().stream().anyMatch(compiledClasses::contains)) {
                typesToReprocess.add(entry.getKey());
            }
        }
        for (String toReprocess : new ArrayList<>(typesToReprocess)) {
            typesToReprocess.removeAll(annotationProcessingData.getGeneratedTypesByOrigin().getOrDefault(toReprocess, Collections.emptySet()));
        }
        return typesToReprocess;
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
