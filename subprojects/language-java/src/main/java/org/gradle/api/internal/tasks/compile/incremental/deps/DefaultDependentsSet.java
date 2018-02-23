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

import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.Set;

public class DefaultDependentsSet implements DependentsSet {

    public static final DependentsSet EMPTY = new DefaultDependentsSet(Collections.<String>emptySet());

    private final Set<String> dependentClasses;

    public DefaultDependentsSet(Set<String> dependentClasses) {
        this.dependentClasses = dependentClasses;
    }

    @Override
    public Set<String> getDependentClasses() {
        return dependentClasses;
    }

    @Override
    public boolean isDependencyToAll() {
        return false;
    }

    @Override
    public String getDescription() {
        return null;
    }

    public static DefaultDependentsSet dependents(String ... dependentClasses) {
        return new DefaultDependentsSet(ImmutableSet.copyOf(dependentClasses));
    }

}
