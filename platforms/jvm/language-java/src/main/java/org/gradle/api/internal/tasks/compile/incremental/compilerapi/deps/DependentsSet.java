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
import org.gradle.internal.collect.PersistentSet;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Provides a set of classes that depend on some other class.
 * If {@link #isDependencyToAll()} returns true, then the dependent classes can't be enumerated.
 * In this case a description of the problem is available via {@link #getDescription()}.
 */
public abstract class DependentsSet {

    public static DependentsSet dependentClasses(PersistentSet<@NonNull String> privateDependentClasses, PersistentSet<@NonNull String> accessibleDependentClasses) {
        return dependents(privateDependentClasses, accessibleDependentClasses, PersistentSet.of());
    }

    public static DependentsSet dependents(PersistentSet<@NonNull String> privateDependentClasses, PersistentSet<@NonNull String> accessibleDependentClasses, PersistentSet<@NonNull GeneratedResource> dependentResources) {
        if (privateDependentClasses.isEmpty() && accessibleDependentClasses.isEmpty() && dependentResources.isEmpty()) {
            return empty();
        } else {
            return new DefaultDependentsSet(privateDependentClasses, accessibleDependentClasses, dependentResources);
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

        for (DependentsSet set : sets) {
            if (set.isDependencyToAll()) {
                return set;
            }
        }

        PersistentSet<@NonNull String> privateDependentClasses = PersistentSet.of();
        PersistentSet<@NonNull String> accessibleDependentClasses = PersistentSet.of();
        PersistentSet<@NonNull GeneratedResource> dependentResources = PersistentSet.of();

        for (DependentsSet set : sets) {
            privateDependentClasses = privateDependentClasses.union(set.getPrivateDependentClasses());
            accessibleDependentClasses = accessibleDependentClasses.union(set.getAccessibleDependentClasses());
            dependentResources = dependentResources.union(set.getDependentResources());
        }
        return DependentsSet.dependents(privateDependentClasses, accessibleDependentClasses, dependentResources);
    }

    public abstract boolean isEmpty();

    public abstract boolean hasDependentClasses();

    public abstract PersistentSet<@NonNull String> getPrivateDependentClasses();

    public abstract PersistentSet<@NonNull String> getAccessibleDependentClasses();

    public abstract PersistentSet<@NonNull GeneratedResource> getDependentResources();

    public abstract boolean isDependencyToAll();

    public abstract String getDescription();

    private DependentsSet() {
    }

    public abstract PersistentSet<@NonNull String> getAllDependentClasses();

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
        public PersistentSet<@NonNull String> getPrivateDependentClasses() {
            return PersistentSet.of();
        }

        @Override
        public PersistentSet<@NonNull String> getAccessibleDependentClasses() {
            return PersistentSet.of();
        }

        @Override
        public PersistentSet<@NonNull String> getAllDependentClasses() {
            return PersistentSet.of();
        }

        @Override
        public PersistentSet<@NonNull GeneratedResource> getDependentResources() {
            return PersistentSet.of();
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

        private final PersistentSet<@NonNull String> privateDependentClasses;
        private final PersistentSet<@NonNull String> accessibleDependentClasses;
        private final PersistentSet<@NonNull GeneratedResource> dependentResources;

        private DefaultDependentsSet(PersistentSet<@NonNull String> privateDependentClasses, PersistentSet<@NonNull String> accessibleDependentClasses, PersistentSet<@NonNull GeneratedResource> dependentResources) {
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
        public PersistentSet<@NonNull String> getPrivateDependentClasses() {
            return privateDependentClasses;
        }

        @Override
        public PersistentSet<@NonNull String> getAccessibleDependentClasses() {
            return accessibleDependentClasses;
        }

        @Override
        public PersistentSet<@NonNull String> getAllDependentClasses() {
            if (privateDependentClasses.isEmpty()) {
                return accessibleDependentClasses;
            }
            if (accessibleDependentClasses.isEmpty()) {
                return privateDependentClasses;
            }
            return accessibleDependentClasses.union(privateDependentClasses);
        }

        @Override
        public PersistentSet<@NonNull GeneratedResource> getDependentResources() {
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
        public PersistentSet<@NonNull String> getPrivateDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public PersistentSet<@NonNull String> getAccessibleDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public PersistentSet<@NonNull String> getAllDependentClasses() {
            throw new UnsupportedOperationException("This dependents set does not have dependent classes information.");
        }

        @Override
        public PersistentSet<@NonNull GeneratedResource> getDependentResources() {
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
