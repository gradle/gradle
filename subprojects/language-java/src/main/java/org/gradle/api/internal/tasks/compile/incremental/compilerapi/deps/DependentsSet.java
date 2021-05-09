/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides a set of classes that depend on some other class.
 * If {@link #isDependencyToAll()} returns true, then the dependent classes can't be enumerated.
 * In this case a description of the problem is available via {@link #getDescription()}.
 */
public abstract class DependentsSet {

    public static DependentsSet dependentClasses(Set<String> privateDependentClasses, Set<String> accessibleDependentClasses) {
        return dependents(privateDependentClasses, accessibleDependentClasses, Collections.emptySet());
    }

    public static DependentsSet dependents(Set<String> privateDependentClasses, Set<String> accessibleDependentClasses, Set<GeneratedResource> dependentResources) {
        if (privateDependentClasses.isEmpty() && accessibleDependentClasses.isEmpty() && dependentResources.isEmpty()) {
            return empty();
        } else {
            return new DefaultDependentsSet(ImmutableSet.copyOf(privateDependentClasses), ImmutableSet.copyOf(accessibleDependentClasses), ImmutableSet.copyOf(dependentResources));
        }
    }

    public static DependentsSet dependencyToAll(String reason) {
        return new DependencyToAll(reason);
    }

    public static DependentsSet empty() {
        return EmptyDependentsSet.INSTANCE;
    }

    public static DependentsSet merge(Collection<DependentsSet> sets) {
        if (sets.isEmpty()) {
            return DependentsSet.empty();
        }
        if (sets.size() == 1) {
            return sets.iterator().next();
        }
        int privateCount = 0;
        int accessibleCount = 0;
        int resourceCount = 0;
        for (DependentsSet set : sets) {
            if (set.isDependencyToAll()) {
                return set;
            }
            privateCount += set.getPrivateDependentClasses().size();
            accessibleCount += set.getAccessibleDependentClasses().size();
            resourceCount += set.getDependentResources().size();
        }

        ImmutableSet.Builder<String> privateDependentClasses = ImmutableSet.builderWithExpectedSize(privateCount);
        ImmutableSet.Builder<String> accessibleDependentClasses = ImmutableSet.builderWithExpectedSize(accessibleCount);
        ImmutableSet.Builder<GeneratedResource> dependentResources = ImmutableSet.builderWithExpectedSize(resourceCount);

        for (DependentsSet set : sets) {
            privateDependentClasses.addAll(set.getPrivateDependentClasses());
            accessibleDependentClasses.addAll(set.getAccessibleDependentClasses());
            dependentResources.addAll(set.getDependentResources());
        }
        return DependentsSet.dependents(privateDependentClasses.build(), accessibleDependentClasses.build(), dependentResources.build());
    }

    public abstract boolean isEmpty();

    public abstract boolean hasDependentClasses();

    public abstract Set<String> getPrivateDependentClasses();

    public abstract Set<String> getAccessibleDependentClasses();

    public abstract Set<GeneratedResource> getDependentResources();

    public abstract boolean isDependencyToAll();

    public abstract String getDescription();

    private DependentsSet() {
    }

    public abstract Set<String> getAllDependentClasses();

    private static class EmptyDependentsSet extends DependentsSet {
        private static final EmptyDependentsSet INSTANCE = new EmptyDependentsSet();

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean hasDependentClasses() {
            return false;
        }

        @Override
        public Set<String> getPrivateDependentClasses() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getAccessibleDependentClasses() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getAllDependentClasses() {
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

        @Override
        public String getDescription() {
            throw new UnsupportedOperationException("This dependents does not have a problem description.");
        }
    }

    private static class DefaultDependentsSet extends DependentsSet {

        private final Set<String> privateDependentClasses;
        private final Set<String> accessibleDependentClasses;
        private final Set<GeneratedResource> dependentResources;

        private DefaultDependentsSet(Set<String> privateDependentClasses, Set<String> accessibleDependentClasses, Set<GeneratedResource> dependentResources) {
            this.privateDependentClasses = privateDependentClasses;
            this.accessibleDependentClasses = accessibleDependentClasses;
            this.dependentResources = dependentResources;
        }

        @Override
        public boolean isEmpty() {
            return !hasDependentClasses() && dependentResources.isEmpty();
        }

        @Override
        public boolean hasDependentClasses() {
            return !privateDependentClasses.isEmpty() || !accessibleDependentClasses.isEmpty();
        }

        @Override
        public Set<String> getPrivateDependentClasses() {
            return privateDependentClasses;
        }

        @Override
        public Set<String> getAccessibleDependentClasses() {
            return accessibleDependentClasses;
        }

        @Override
        public Set<String> getAllDependentClasses() {
            if (privateDependentClasses.isEmpty()) {
                return accessibleDependentClasses;
            }
            if (accessibleDependentClasses.isEmpty()) {
                return privateDependentClasses;
            }
            Set<String> r = new HashSet<>(accessibleDependentClasses);
            r.addAll(privateDependentClasses);
            return r;
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
            throw new UnsupportedOperationException("This dependents does not have a problem description.");
        }
    }

    private static class DependencyToAll extends DependentsSet {

        private final String reason;

        private DependencyToAll(String reason) {
            this.reason = Preconditions.checkNotNull(reason);
        }

        private DependencyToAll() {
            this(null);
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public boolean hasDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getPrivateDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getAccessibleDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getAllDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public Set<GeneratedResource> getDependentResources() {
            throw new UnsupportedOperationException("This dependents set does not have dependent resources information.");
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
