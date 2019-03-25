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
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public abstract class DependentsSet {

    public static DependentsSet dependentClasses(String... dependentClasses) {
        if (dependentClasses.length == 0) {
            return empty();
        } else {
            return new DefaultDependentsSet(ImmutableSet.copyOf(dependentClasses), Collections.<GeneratedResource>emptySet());
        }
    }

    public static DependentsSet dependentClasses(Set<String> dependentClasses) {
        return dependents(dependentClasses, Collections.<GeneratedResource>emptySet());
    }

    public static DependentsSet dependents(Set<String> dependentClasses, Set<GeneratedResource> dependentResources) {
        if (dependentClasses.isEmpty() && dependentResources.isEmpty()) {
            return empty();
        } else {
            return new DefaultDependentsSet(ImmutableSet.copyOf(dependentClasses), ImmutableSet.copyOf(dependentResources));
        }
    }

    public static DependentsSet dependencyToAll() {
        return DependencyToAll.INSTANCE;
    }

    public static DependentsSet dependencyToAll(String reason) {
        return new DependencyToAll(reason);
    }

    public static DependentsSet empty() {
        return EmptyDependentsSet.INSTANCE;
    }

    public abstract Set<String> getDependentClasses();

    public abstract Set<GeneratedResource> getDependentResources();

    public abstract boolean isDependencyToAll();

    public abstract @Nullable String getDescription();

    private DependentsSet() {
    }

    private static class EmptyDependentsSet extends DependentsSet {
        private static final EmptyDependentsSet INSTANCE = new EmptyDependentsSet();

        @Override
        public Set<String> getDependentClasses() {
            return Collections.emptySet();
        }

        @Override
        public Set<GeneratedResource> getDependentResources() {
            return Collections.emptySet();
        }

        @Override
        public boolean isDependencyToAll() {
            return false;
        }

        @Nullable
        @Override
        public String getDescription() {
            return null;
        }
    }

    private static class DefaultDependentsSet extends DependentsSet {

        private final Set<String> dependentClasses;
        private final Set<GeneratedResource> dependentResources;

        private DefaultDependentsSet(Set<String> dependentClasses, Set<GeneratedResource> dependentResources) {
            this.dependentClasses = dependentClasses;
            this.dependentResources = dependentResources;
        }

        @Override
        public Set<String> getDependentClasses() {
            return dependentClasses;
        }

        @Override
        public Set<GeneratedResource> getDependentResources() {
            return dependentResources;
        }

        @Override
        public boolean isDependencyToAll() {
            return false;
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    private static class DependencyToAll extends DependentsSet {
        private static final DependencyToAll INSTANCE = new DependencyToAll();

        private final String reason;

        private DependencyToAll(String reason) {
            this.reason = reason;
        }

        private DependencyToAll() {
            this(null);
        }

        @Override
        public Set<String> getDependentClasses() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent classes information.");
        }

        @Override
        public Set<GeneratedResource> getDependentResources() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent resources information.");
        }

        @Override
        public boolean isDependencyToAll() {
            return true;
        }

        @Override
        public String getDescription() {
            return reason;
        }
    }
}
