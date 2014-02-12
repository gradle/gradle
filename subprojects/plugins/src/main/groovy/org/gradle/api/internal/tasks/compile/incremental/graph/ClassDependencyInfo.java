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

import org.apache.commons.lang.mutable.MutableBoolean;
import org.gradle.api.internal.tasks.compile.incremental.ClassDependents;
import org.gradle.api.internal.tasks.compile.incremental.DummySerializer;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 1/15/14
 */
public class ClassDependencyInfo implements Serializable {

    private final Map<String, ClassDependents> dependents;

    public ClassDependencyInfo(Map<String, ClassDependents> dependents) {
        this.dependents = dependents;
    }

    public Set<String> getActualDependents(String className) {
        Set<String> out = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        MutableBoolean isDependentToAll = new MutableBoolean(false);
        recurseDependents(visited, out, className, isDependentToAll);
        if (isDependentToAll.isTrue()) {
            return null;
        }
        out.remove(className);
        return out;
    }

    private void recurseDependents(Set<String> visited, Collection<String> accumulator, String className, MutableBoolean dependentToAll) {
        if (!visited.add(className)) {
            return;
        }
        ClassDependents out = dependents.get(className);
        if (out == null) {
            return;
        }
        if (out.isDependentToAll()) {
            dependentToAll.setValue(true);
            return;
        }
        for (String dependent : out.getDependentClasses()) {
            if (!dependent.contains("$") && !dependent.equals(className)) { //naive
                accumulator.add(dependent);
            }
            recurseDependents(visited, accumulator, dependent, dependentToAll);
        }
    }
}
