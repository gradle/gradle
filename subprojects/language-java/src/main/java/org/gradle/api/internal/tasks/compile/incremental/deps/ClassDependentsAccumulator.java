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
import com.google.common.collect.Multimap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassDependentsAccumulator {

    private final Map<String, DependentsSet> dependents = new HashMap<String, DependentsSet>();
    private final Multimap<String, Integer> classesToConstants = HashMultimap.create();
    private final Multimap<Integer, String> literalsToClasses = HashMultimap.create();
    private final String packagePrefix;

    public ClassDependentsAccumulator(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    public void addClass(String className, boolean dependencyToAll, Iterable<String> classDependencies, Set<Integer> constants, Set<Integer> literals) {
        if (className.startsWith(packagePrefix)) {
            rememberClass(className).setDependencyToAll(dependencyToAll);
        }
        for (String dependency : classDependencies) {
            if (!dependency.equals(className) && dependency.startsWith(packagePrefix)) {
                DefaultDependentsSet d = rememberClass(dependency);
                if (className.startsWith(packagePrefix)) {
                    d.addDependent(className);
                }
            }
        }
        for (Integer constant : constants) {
            classesToConstants.put(className, constant);
        }
        for (Integer literal : literals) {
            literalsToClasses.put(literal, className);
        }
    }

    private DefaultDependentsSet rememberClass(String className) {
        DependentsSet d = dependents.get(className);
        if (d == null) {
            d = new DefaultDependentsSet();
            dependents.put(className, d);
        }
        return (DefaultDependentsSet) d;
    }

    public Map<String, DependentsSet> getDependentsMap() {
        return dependents;
    }

    public Multimap<String, Integer> getClassesToConstants() {
        return classesToConstants;
    }

    public Multimap<Integer, String> getLiteralsToClasses() {
        return literalsToClasses;
    }
}
