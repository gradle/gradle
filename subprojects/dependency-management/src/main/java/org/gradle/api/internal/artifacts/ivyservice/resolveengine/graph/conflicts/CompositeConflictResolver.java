/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

class CompositeConflictResolver implements ModuleConflictResolver {

    private final List<ModuleConflictResolver> resolvers = new LinkedList<ModuleConflictResolver>();

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        CompositeDetails<T> composite = new CompositeDetails<T>(details);
        for (ModuleConflictResolver r : resolvers) {
            r.select(composite);
            if (composite.hasResult) {
                return;
            }
        }
        throw new IllegalStateException(this.getClass().getSimpleName()
                + " was unable to select a candidate using resolvers: " + resolvers + ". Candidates: " + details.getCandidates());
    }

    void addFirst(ModuleConflictResolver conflictResolver) {
        resolvers.add(0, conflictResolver);
    }

    private static class CompositeDetails<T extends ComponentResolutionState> implements ConflictResolverDetails<T> {
        private final ConflictResolverDetails<T> delegate;
        private boolean hasResult;

        private CompositeDetails(ConflictResolverDetails<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Collection<? extends T> getCandidates() {
            return delegate.getCandidates();
        }

        @Override
        public void select(T candidate) {
            hasResult = true;
            delegate.select(candidate);
        }

        @Override
        public void fail(Throwable error) {
            hasResult = true;
            delegate.fail(error);
        }

        @Override
        public T getSelected() {
            return delegate.getSelected();
        }

        @Override
        public boolean isRestart() {
            return delegate.isRestart();
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return delegate.getFailure();
        }

        @Override
        public boolean hasFailure() {
            return delegate.hasFailure();
        }

        @Override
        public boolean hasSelected() {
            return delegate.hasSelected();
        }
    }
}
