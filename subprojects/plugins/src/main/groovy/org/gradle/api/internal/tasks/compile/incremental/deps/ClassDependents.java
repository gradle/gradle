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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClassDependents implements Serializable, DependentsSet {

    private final Set<String> dependentClasses = new HashSet<String>();
    private boolean dependencyToAll;

    public ClassDependents(boolean dependencyToAll) {
        this(dependencyToAll, Collections.<String>emptyList());
    }

    public ClassDependents(Collection<String> dependentClasses) {
        this(false, dependentClasses);
    }

    public ClassDependents(boolean dependencyToAll, Collection<String> dependentClasses) {
        this.dependencyToAll = dependencyToAll;
        this.dependentClasses.addAll(dependentClasses);
    }

    public ClassDependents() {}

    public Set<String> getDependentClasses() {
        return dependentClasses;
    }

    public boolean isDependencyToAll() {
        return dependencyToAll;
    }

    public ClassDependents addDependent(String className) {
        dependentClasses.add(className);
        return this;
    }

    //TODO SF review the necessity of those guys.
    public static ClassDependents dependencyToAll() {
        return new ClassDependents(true);
    }

    public static ClassDependents emptyDependents() {
        return dependentsSet(Collections.<String>emptyList());
    }

    public static ClassDependents dependentsSet(Collection<String> dependentClasses) {
        return new ClassDependents(dependentClasses);
    }

    public void setDependencyToAll(boolean dependencyToAll) {
        this.dependencyToAll = dependencyToAll;
    }
}
