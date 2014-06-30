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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ClassSetAnalysis {

    private final ClassSetAnalysisData data;

    public ClassSetAnalysis(ClassSetAnalysisData data) {
        this.data = data;
    }

    public DependentsSet getRelevantDependents(Iterable<String> classes) {
        List<String> result = new LinkedList<String>();
        for (String cls : classes) {
            DependentsSet d = getRelevantDependents(cls);
            if (d.isDependencyToAll()) {
                return d;
            }
            result.addAll(d.getDependentClasses());
        }
        return new DefaultDependentsSet(result);
    }

    public DependentsSet getRelevantDependents(String className) {
        DependentsSet deps = data.getDependents(className);
        if (deps == null) {
            return new DefaultDependentsSet();
        }
        if (deps.isDependencyToAll()) {
            return new DependencyToAll();
        }
        Set<String> result = new HashSet<String>();
        recurseDependents(new HashSet<String>(), result, deps.getDependentClasses());
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
            recurseDependents(visited, result, currentDependents.getDependentClasses());
        }
    }

    public ClassSetAnalysisData getData() {
        return data;
    }
}