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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.HashSet;
import java.util.Set;

public class ClassSetAnalysis {

    private final ClassSetAnalysisData data;

    public ClassSetAnalysis(ClassSetAnalysisData data) {
        this.data = data;
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
        DependentsSet deps = data.getDependents(className);
        if (deps.isDependencyToAll()) {
            return deps;
        }
        if (!constants.isEmpty()) {
            return DependentsSet.dependencyToAll();
        }
        DependentsSet dependentsOnAll = data.getDependentsOnAll();
        if (deps.getDependentClasses().isEmpty() && dependentsOnAll.getDependentClasses().isEmpty()) {
            return deps;
        }
        Set<String> result = new HashSet<String>();
        recurseDependents(new HashSet<String>(), result, Iterables.concat(deps.getDependentClasses(), dependentsOnAll.getDependentClasses()));
        result.remove(className);
        return DependentsSet.dependents(result);
    }

    public boolean isDependencyToAll(String className) {
        return data.getDependents(className).isDependencyToAll();
    }

    private void recurseDependents(Set<String> visited, Set<String> result, Iterable<String> dependentClasses) {
        for (String d : dependentClasses) {
            if (!visited.add(d)) {
                continue;
            }
            if (!isNestedClass(d)) {
                result.add(d);
            }
            DependentsSet currentDependents = data.getDependents(d);
            if (!currentDependents.isDependencyToAll()) {
                recurseDependents(visited, result, currentDependents.getDependentClasses());
            }
        }
    }

    private boolean isNestedClass(String d) {
        return d.contains("$");
    }

    public ClassSetAnalysisData getData() {
        return data;
    }
}
