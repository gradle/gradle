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

package org.gradle.api.internal.tasks.compile.incremental;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClassDependents implements Serializable, DependentsSet {

    private final Set<String> dependentClasses;

    private ClassDependents(Collection<String> dependentClasses) {
        if (dependentClasses == null) {
            this.dependentClasses = null;
        } else {
            this.dependentClasses = new LinkedHashSet<String>(dependentClasses);
        }
    }

    public Set<String> getDependentClasses() {
        return dependentClasses;
    }

    public boolean isDependencyToAll() {
        return dependentClasses == null;
    }

    public ClassDependents addClass(String className) {
        if (dependentClasses == null) {
            throw new UnsupportedOperationException("This dependents set is a dependency to all.");
        }
        dependentClasses.add(className);
        return this;
    }

    public static ClassDependents dependencyToAll() {
        return dependentsSet(null);
    }

    public static ClassDependents emptyDependents() {
        return dependentsSet(Collections.<String>emptyList());
    }

    public static ClassDependents dependentsSet(Collection<String> dependentClasses) {
        return new ClassDependents(dependentClasses);
    }
}
