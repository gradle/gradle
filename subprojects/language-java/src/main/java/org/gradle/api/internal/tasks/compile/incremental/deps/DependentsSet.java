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
import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public abstract class DependentsSet {

    public static DependentsSet dependentClasses(Set<String> privateDependentClasses, Set<String> accessibleDependentClasses) {
        return dependents(privateDependentClasses, accessibleDependentClasses, Collections.<GeneratedResource>emptySet());
    }

    public static DependentsSet dependents(Set<String> privateDependentClasses, Set<String> accessibleDependentClasses, Set<GeneratedResource> dependentResources) {
        if (privateDependentClasses.isEmpty() && accessibleDependentClasses.isEmpty() && dependentResources.isEmpty()) {
            return empty();
        } else {
            return new DefaultDependentsSet(ImmutableSet.copyOf(privateDependentClasses), ImmutableSet.copyOf(accessibleDependentClasses), ImmutableSet.copyOf(dependentResources));
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

    public abstract boolean isEmpty();

    public abstract boolean hasDependentClasses();

    public abstract Set<String> getPrivateDependentClasses();

    public abstract Set<String> getAccessibleDependentClasses();

    public abstract Set<GeneratedResource> getDependentResources();

    public abstract boolean isDependencyToAll();

    public abstract @Nullable String getDescription();

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

        @Nullable
        @Override
        public String getDescription() {
            return null;
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
            Set<String> r = Sets.newHashSet(accessibleDependentClasses);
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
        public boolean isEmpty() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent classes information.");
        }

        @Override
        public boolean hasDependentClasses() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getPrivateDependentClasses() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getAccessibleDependentClasses() {
            throw new UnsupportedOperationException("This instance of dependents set does not have dependent classes information.");
        }

        @Override
        public Set<String> getAllDependentClasses() {
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
