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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassDependentsAccumulator {

    private final Set<String> dependenciesToAll = Sets.newHashSet();
    private final Set<String> dependentsOnAll = Sets.newHashSet();
    private final Map<String, String> filePathToClassName = new HashMap<String, String>();
    private final Map<String, Set<String>> dependents = new HashMap<String, Set<String>>();
    private final Map<String, IntSet> classesToConstants = new HashMap<String, IntSet>();
    private final Set<String> seenClasses = Sets.newHashSet();
    private final Multimap<String, String> parentToChildren = HashMultimap.create();
    private String fullRebuildCause;

    public ClassDependentsAccumulator() {
    }

    public void addClass(File classFile, ClassAnalysis classAnalysis) {
        addClass(classAnalysis);
        filePathToClassName.put(classFile.getAbsolutePath(), classAnalysis.getClassName());
    }

    public void addClass(ClassAnalysis classAnalysis) {
        addClass(classAnalysis.getClassName(), classAnalysis.isDependencyToAll(), classAnalysis.getClassDependencies(), classAnalysis.getConstants(), classAnalysis.getSuperTypes());
    }

    public void addClass(String className, boolean dependencyToAll, Iterable<String> classDependencies, IntSet constants, Set<String> superTypes) {
        if (seenClasses.contains(className)) {
            // same classes may be found in different classpath trees/jars
            // and we keep only the first one
            return;
        }
        seenClasses.add(className);
        classesToConstants.put(className, constants);
        if (dependencyToAll) {
            dependenciesToAll.add(className);
            dependents.remove(className);
        }
        for (String dependency : classDependencies) {
            if (!dependency.equals(className) && !dependenciesToAll.contains(dependency)) {
                Set<String> d = rememberClass(dependency);
                d.add(className);
            }
        }
        for (String superType : superTypes) {
            parentToChildren.put(superType, className);
        }
    }

    private Set<String> rememberClass(String className) {
        Set<String> d = dependents.get(className);
        if (d == null) {
            d = Sets.newHashSet();
            dependents.put(className, d);
        }
        return d;
    }

    public Map<String, DependentsSet> getDependentsMap() {
        if (dependenciesToAll.isEmpty() && dependents.isEmpty()) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<String, DependentsSet> builder = ImmutableMap.builder();
        for (String s : dependenciesToAll) {
            builder.put(s, DependentsSet.dependencyToAll());
        }
        for (Map.Entry<String, Set<String>> entry : dependents.entrySet()) {
            builder.put(entry.getKey(), DependentsSet.dependents(entry.getValue()));
        }
        return builder.build();
    }

    public Map<String, IntSet> getClassesToConstants() {
        return classesToConstants;
    }

    public void addAnnotationProcessingResult(AnnotationProcessingResult annotationProcessingResult) {
        for (Map.Entry<String, Set<String>> entry : annotationProcessingResult.getGeneratedTypesByOrigin().entrySet()) {
            Set<String> dependents = rememberClass(entry.getKey());
            dependents.addAll(entry.getValue());
        }
        dependentsOnAll.addAll(annotationProcessingResult.getAggregatedTypes());
    }

    public void fullRebuildNeeded(String fullRebuildCause) {
        this.fullRebuildCause = fullRebuildCause;
    }

    public ClassSetAnalysisData getAnalysis() {
        return new ClassSetAnalysisData(filePathToClassName, getDependentsMap(), getClassesToConstants(), asMap(parentToChildren), DependentsSet.dependents(dependentsOnAll), fullRebuildCause);
    }

    private static <K, V> Map<K, Set<V>> asMap(Multimap<K, V> multimap) {
        ImmutableMap.Builder<K, Set<V>> builder = ImmutableMap.builder();
        for (K key : multimap.keySet()) {
            builder.put(key, ImmutableSet.copyOf(multimap.get(key)));
        }
        return builder.build();
    }
}
