/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.subgraphconstraints;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Collections;
import java.util.Set;

public class SubgraphConstraints {

    public static final SubgraphConstraints EMPTY = new SubgraphConstraints() {
        @Override
        public final SubgraphConstraints union(SubgraphConstraints other) {
            return other;
        }

        @Override
        public final SubgraphConstraints intersect(SubgraphConstraints other) {
            return EMPTY;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(ModuleIdentifier module) {
            return false;
        }

        @Override
        public String toString() {
            return "no modules";
        }
    };

    private final Set<ModuleIdentifier> modules;

    private SubgraphConstraints() {
        modules = Collections.emptySet();
    }

    private SubgraphConstraints(Set<ModuleIdentifier> modules) {
        this.modules = modules;
    }

    public static SubgraphConstraints of(Set<ModuleIdentifier> modules) {
        if (modules.isEmpty()) {
            return EMPTY;
        }
        return new SubgraphConstraints(modules);
    }

    public Set<ModuleIdentifier> getModules() {
        return modules;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean contains(ModuleIdentifier module) {
        return modules.contains(module);
    }

    public SubgraphConstraints union(SubgraphConstraints other) {
        if (other == EMPTY) {
            return this;
        }
        ImmutableSet.Builder<ModuleIdentifier> builder = ImmutableSet.builderWithExpectedSize(modules.size() + other.modules.size());
        builder.addAll(modules);
        builder.addAll(other.modules);
        return of(builder.build());
    }

    public SubgraphConstraints intersect(SubgraphConstraints other) {
        if (other.modules == modules) {
            return this;
        }
        if (other == EMPTY) {
            return EMPTY;
        }
        return of(ImmutableSet.copyOf(Sets.intersection(modules, other.modules)));
    }

    @Override
    public String toString() {
        return "modules=" + modules;
    }
}
