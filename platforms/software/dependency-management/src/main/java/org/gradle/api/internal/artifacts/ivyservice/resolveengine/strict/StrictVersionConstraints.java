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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict;

import io.usethesource.capsule.Set;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class StrictVersionConstraints {

    public static final StrictVersionConstraints EMPTY = new StrictVersionConstraints(Set.Immutable.of()) {
        @Override
        public StrictVersionConstraints union(StrictVersionConstraints other) {
            return other;
        }

        @Override
        public StrictVersionConstraints intersect(StrictVersionConstraints other) {
            return EMPTY;
        }

        @Override
        public StrictVersionConstraints minus(StrictVersionConstraints other) {
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

    private final Set.Immutable<ModuleIdentifier> modules;

    private StrictVersionConstraints(Set.Immutable<ModuleIdentifier> modules) {
        this.modules = modules;
    }

    public static StrictVersionConstraints of(java.util.Set<ModuleIdentifier> modules) {
        return of(Set.Immutable.<ModuleIdentifier>of().__insertAll(modules));
    }

    public static StrictVersionConstraints of(Set.Immutable<ModuleIdentifier> modules) {
        if (modules.isEmpty()) {
            return EMPTY;
        }
        return new StrictVersionConstraints(modules);
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

    public StrictVersionConstraints union(StrictVersionConstraints other) {
        if (other == EMPTY) {
            return this;
        }
        if (this == other) {
            // this happens quite a lot!
            return this;
        }
        if (this.modules.equals(other.modules)) {
            return this;
        }
        return of(modules.union(other.modules));
    }

    public StrictVersionConstraints intersect(StrictVersionConstraints other) {
        if (other.modules == modules) {
            return this;
        }
        if (other == EMPTY) {
            return EMPTY;
        }
        return of(modules.intersect(other.modules));
    }

    @Override
    public String toString() {
        return "modules=" + modules;
    }

    public StrictVersionConstraints minus(StrictVersionConstraints other) {
        if (other == EMPTY) {
            return this;
        }

        if (this == other || this == EMPTY) {
            return EMPTY;
        }

        return of(modules.subtract(other.modules));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StrictVersionConstraints that = (StrictVersionConstraints) o;
        return modules.equals(that.modules);
    }

    @Override
    public int hashCode() {
        return modules.hashCode();
    }

}
