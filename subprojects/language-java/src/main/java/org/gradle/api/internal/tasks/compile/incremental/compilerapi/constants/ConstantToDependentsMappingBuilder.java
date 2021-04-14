/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A builder helper class to construct the ConstantToDependentsMapping
 */
public class ConstantToDependentsMappingBuilder implements Serializable {

    private final Set<String> visitedClasses;
    private final List<String> dependents;
    private final Map<Integer, Set<Integer>> privateDependentsIndexes;
    private final Map<Integer, Set<Integer>> accessibleDependentsIndexes;
    private final Map<String, Integer> classNameToIndex;

    ConstantToDependentsMappingBuilder() {
        this.visitedClasses = new HashSet<>();
        this.dependents = new ArrayList<>();
        this.classNameToIndex = new HashMap<>();
        this.privateDependentsIndexes = new HashMap<>();
        this.accessibleDependentsIndexes = new HashMap<>();
    }

    public ConstantToDependentsMappingBuilder addAccessibleDependents(int constantOriginHash, Collection<String> dependents) {
        dependents.forEach(dependent -> addAccessibleDependent(constantOriginHash, dependent));
        return this;
    }

    public ConstantToDependentsMappingBuilder addPrivateDependents(int constantOriginHash, Collection<String> dependents) {
        dependents.forEach(dependent -> addPrivateDependent(constantOriginHash, dependent));
        return this;
    }

    public ConstantToDependentsMappingBuilder addPrivateDependent(int constantOriginHash, String dependent) {
        Set<Integer> accessibleDependents = accessibleDependentsIndexes.getOrDefault(constantOriginHash, Collections.emptySet());
        Set<Integer> privateDependents = privateDependentsIndexes.computeIfAbsent(constantOriginHash, k -> new HashSet<>());
        int dependentIndex = classNameToIndex.getOrDefault(dependent, -1);
        if (dependentIndex < 0 || !accessibleDependents.contains(dependentIndex)) {
            addDependent(privateDependents, dependent);
        }
        return this;
    }

    public ConstantToDependentsMappingBuilder addAccessibleDependent(int constantOriginHash, String dependent) {
        Set<Integer> accessibleDependents = accessibleDependentsIndexes.computeIfAbsent(constantOriginHash, k -> new HashSet<>());
        Set<Integer> privateDependents = privateDependentsIndexes.getOrDefault(constantOriginHash, Collections.emptySet());
        int dependentIndex = addDependent(accessibleDependents, dependent);
        if (!privateDependents.isEmpty()) {
            privateDependents.remove(dependentIndex);
        }
        return this;
    }

    public ConstantToDependentsMappingBuilder addVisitedClass(String visitedClass) {
        visitedClasses.add(visitedClass);
        return this;
    }

    private int addDependent(Set<Integer> dependentIndexes, String dependent) {
        int dependentIndex = classNameToIndex.computeIfAbsent(dependent, k -> {
            addVisitedClass(dependent);
            dependents.add(dependent);
            return dependents.size() - 1;
        });
        dependentIndexes.add(dependentIndex);
        return dependentIndex;
    }

    public ConstantToDependentsMapping build() {
        privateDependentsIndexes.values().removeIf(Set::isEmpty);
        accessibleDependentsIndexes.values().removeIf(Set::isEmpty);
        return new ConstantToDependentsMapping(visitedClasses, dependents, privateDependentsIndexes, accessibleDependentsIndexes);
    }

}
