/*
 * Copyright 2017 the original author or authors.
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
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import java.util.Set;

/**
 * An immutable set of details extracted from a class file.
 */
public class ClassAnalysis {
    private final String className;
    private final Set<String> privateClassDependencies;
    private final Set<String> accessibleClassDependencies;
    private final boolean dependencyToAll;
    private final IntSet constants;

    public ClassAnalysis(String className, Set<String> privateClassDependencies, Set<String> accessibleClassDependencies, boolean dependencyToAll, IntSet constants) {
        this.className = className;
        this.privateClassDependencies = ImmutableSet.copyOf(privateClassDependencies);
        this.accessibleClassDependencies = ImmutableSet.copyOf(accessibleClassDependencies);
        this.dependencyToAll = dependencyToAll;
        this.constants = constants.isEmpty() ? IntSets.EMPTY_SET : constants;
    }

    public String getClassName() {
        return className;
    }

    public Set<String> getPrivateClassDependencies() {
        return privateClassDependencies;
    }

    public Set<String> getAccessibleClassDependencies() {
        return accessibleClassDependencies;
    }

    public IntSet getConstants() {
        return constants;
    }

    public boolean isDependencyToAll() {
        return dependencyToAll;
    }
}
