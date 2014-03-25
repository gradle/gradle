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

package org.gradle.api.internal.tasks.compile.incremental.graph;

import org.gradle.api.internal.tasks.compile.incremental.ClassDependents;

import java.io.Serializable;
import java.util.*;

public class ClassDependencyInfo implements Serializable {

    private final Map<String, ClassDependents> dependents;

    public ClassDependencyInfo(Map<String, ClassDependents> dependents) {
        this.dependents = dependents;
    }

    public Set<String> getActualDependents(String className) {
        Set<String> out = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        ClassDependents deps = dependents.get(className);
        if (deps == null) {
            return Collections.emptySet();
        }
        if (deps.isDependentToAll()) {
            return null;
        }
        recurseDependents(visited, out, className, deps);
        out.remove(className);
        return out;
    }

    private void recurseDependents(Set<String> visited, Collection<String> accumulator, String className, ClassDependents incomingDependents) {
        if (!visited.add(className) || incomingDependents == null) {
            return;
        }
        for (String dependent : incomingDependents.getDependentClasses()) {
            if (!dependent.contains("$") && !dependent.equals(className)) { //naive
                accumulator.add(dependent);
            }
            ClassDependents currentDependents = this.dependents.get(dependent);
            recurseDependents(visited, accumulator, dependent, currentDependents);
        }
    }
}
