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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.collect.PersistentSet;
import org.jspecify.annotations.NullMarked;

@NullMarked
@SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
public class StrictVersionConstraints {

    public static final StrictVersionConstraints EMPTY = new StrictVersionConstraints(PersistentSet.of()) {
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

    private final PersistentSet<ModuleIdentifier> modules;

    private StrictVersionConstraints(PersistentSet<ModuleIdentifier> modules) {
        this.modules = modules;
    }

    public static StrictVersionConstraints of(PersistentSet<ModuleIdentifier> modules) {
        if (modules.isEmpty()) {
            return EMPTY;
        }
        return new StrictVersionConstraints(modules);
    }

    public PersistentSet<ModuleIdentifier> getModules() {
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
        PersistentSet<ModuleIdentifier> union = this.modules.union(other.modules);
        if (union == this.modules) {
            return this;
        }
        return of(union);
    }

    public StrictVersionConstraints intersect(StrictVersionConstraints other) {
        if (other == EMPTY) {
            return EMPTY;
        }
        if (this == other) {
            return this;
        }
        PersistentSet<ModuleIdentifier> intersect = this.modules.intersect(other.modules);
        if (intersect == this.modules) {
            return this;
        }
        return of(intersect);
    }

    @Override
    public String toString() {
        return "modules=" + modules;
    }

    public StrictVersionConstraints minus(StrictVersionConstraints other) {
        if (other == EMPTY) {
            return this;
        }
        if (this == other) {
            return EMPTY;
        }
        PersistentSet<ModuleIdentifier> diff = this.modules.except(other.modules);
        if (diff == this.modules) {
            return this;
        }
        return of(diff);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
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
