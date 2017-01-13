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

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;

public class ClassSetAnalysis {

    private final ClassSetAnalysisData data;

    public ClassSetAnalysis(ClassSetAnalysisData data) {
        this.data = data;
    }

    public DependentsSet getRelevantDependents(Iterable<String> classes, Set<Integer> constants) {
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
            for (String dependentClass : dependentClasses) {
                result.add(dependentClass);
                Set<String> children = data.classesToChildren.get(dependentClass);
                if (children!=null && children.contains(cls)) {
                    System.out.println("children = " + children);
                }
            }
            result.addAll(dependentClasses);
        }
        return result == null ? DefaultDependentsSet.EMPTY : new DefaultDependentsSet(result);
    }

    public DependentsSet getRelevantDependents(String className, Set<Integer> constants) {
        DependentsSet deps = data.getDependents(className);
        if (deps != null && deps.isDependencyToAll()) {
            return deps;
        }
        if (deps == null && constants.isEmpty()) {
            return DefaultDependentsSet.EMPTY;
        }
        Set<String> result = new HashSet<String>();
        if (deps != null && !deps.isDependencyToAll()) {
            recurseDependents(new HashSet<String>(), result, deps.getDependentClasses());
        }
        for (Integer constant : constants) {
            Set<String> classes = data.literalsToClasses.get(constant);
            if (classes != null) {
                result.addAll(classes);
            }
        }

        result.remove(className);
        return new DefaultDependentsSet(result);
    }

    public boolean isDependencyToAll(String className) {
        DependentsSet deps = data.getDependents(className);
        return deps != null && deps.isDependencyToAll();
    }

    private void recurseDependents(Set<String> visited, Set<String> result, Set<String> dependentClasses) {
        for (String d : dependentClasses) {
            if (!visited.add(d)) {
                continue;
            }
            if (!d.contains("$")) { //filter out the inner classes
                result.add(d);
            }
            DependentsSet currentDependents = data.getDependents(d);
            if (currentDependents != null && !currentDependents.isDependencyToAll()) {
                recurseDependents(visited, result, currentDependents.getDependentClasses());
            }
        }
    }

    public ClassSetAnalysisData getData() {
        return data;
    }
}
