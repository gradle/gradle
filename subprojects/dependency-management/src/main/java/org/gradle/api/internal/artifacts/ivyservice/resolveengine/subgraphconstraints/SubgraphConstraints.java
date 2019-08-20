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
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Collections;
import java.util.Set;

public class SubgraphConstraints {

    public static final SubgraphConstraints EMPTY = new SubgraphConstraints();

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

    public static SubgraphConstraints of(SubgraphConstraints subgraphConstraints1, SubgraphConstraints subgraphConstraints2) {
        return of(new ImmutableSet.Builder<ModuleIdentifier>().addAll(subgraphConstraints1.modules).addAll(subgraphConstraints2.modules).build());
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
}
