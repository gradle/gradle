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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassDependentsAccumulator {

    private final Set<String> dependenciesToAll = Sets.newHashSet();
    private final Map<String, Set<String>> privateDependents = new HashMap<String, Set<String>>();
    private final Map<String, Set<String>> accessibleDependents = new HashMap<String, Set<String>>();
    private final ImmutableMap.Builder<String, IntSet> classesToConstants = ImmutableMap.builder();
    private final Set<String> seenClasses = Sets.newHashSet();
    private String fullRebuildCause;

    public void addClass(ClassAnalysis classAnalysis) {
        addClass(classAnalysis.getClassName(), classAnalysis.isDependencyToAll(), classAnalysis.getPrivateClassDependencies(), classAnalysis.getAccessibleClassDependencies(), classAnalysis.getConstants());
    }

    public void addClass(String className, boolean dependencyToAll, Iterable<String> privateClassDependencies, Iterable<String> accessibleClassDependencies, IntSet constants) {
        if (seenClasses.contains(className)) {
            // same classes may be found in different classpath trees/jars
            // and we keep only the first one
            return;
        }
        seenClasses.add(className);
        if (!constants.isEmpty()) {
            classesToConstants.put(className, constants);
        }
        if (dependencyToAll) {
            dependenciesToAll.add(className);
            privateDependents.remove(className);
            accessibleDependents.remove(className);
        }
        for (String dependency : privateClassDependencies) {
            if (!dependency.equals(className) && !dependenciesToAll.contains(dependency)) {
                addDependency(privateDependents, dependency, className);
            }
        }
        for (String dependency : accessibleClassDependencies) {
            if (!dependency.equals(className) && !dependenciesToAll.contains(dependency)) {
                addDependency(accessibleDependents, dependency, className);
            }
        }
    }

    private Set<String> rememberClass(Map<String, Set<String>> dependents, String className) {
        Set<String> d = dependents.get(className);
        if (d == null) {
            d = Sets.newHashSet();
            dependents.put(className, d);
        }
        return d;
    }

    @VisibleForTesting
    Map<String, DependentsSet> getDependentsMap() {
        if (dependenciesToAll.isEmpty() && privateDependents.isEmpty() && accessibleDependents.isEmpty()) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<String, DependentsSet> builder = ImmutableMap.builder();
        for (String s : dependenciesToAll) {
            builder.put(s, DependentsSet.dependencyToAll());
        }
        Set<String> collected = Sets.newHashSet();
        for (Map.Entry<String, Set<String>> entry : accessibleDependents.entrySet()) {
            if (collected.add(entry.getKey())) {
                builder.put(entry.getKey(), DependentsSet.dependentClasses(privateDependents.getOrDefault(entry.getKey(), Collections.emptySet()), entry.getValue()));
            }
        }
        for (Map.Entry<String, Set<String>> entry : privateDependents.entrySet()) {
            if (collected.add(entry.getKey())) {
                builder.put(entry.getKey(), DependentsSet.dependentClasses(entry.getValue(), accessibleDependents.getOrDefault(entry.getKey(), Collections.emptySet())));
            }
        }
        return builder.build();
    }

    @VisibleForTesting
    Map<String, IntSet> getClassesToConstants() {
        return classesToConstants.build();
    }

    private void addDependency(Map<String, Set<String>> dependentsMap, String dependency, String dependent) {
        Set<String> dependents = rememberClass(dependentsMap, dependency);
        dependents.add(dependent);
    }

    public void fullRebuildNeeded(String fullRebuildCause) {
        this.fullRebuildCause = fullRebuildCause;
    }

    public ClassSetAnalysisData getAnalysis() {
        return new ClassSetAnalysisData(ImmutableSet.copyOf(seenClasses), getDependentsMap(), getClassesToConstants(), fullRebuildCause);
    }

    private static <K, V> Map<K, Set<V>> asMap(Multimap<K, V> multimap) {
        ImmutableMap.Builder<K, Set<V>> builder = ImmutableMap.builder();
        for (K key : multimap.keySet()) {
            builder.put(key, ImmutableSet.copyOf(multimap.get(key)));
        }
        return builder.build();
    }
}
