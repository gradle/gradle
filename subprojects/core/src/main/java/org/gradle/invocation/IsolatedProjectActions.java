/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.invocation;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.internal.isolation.Isolatable;

import javax.annotation.Nullable;
import java.util.Collection;

class IsolatedProjectActions {

    interface Host {
        <T> ImmutableList<Isolatable<T>> isolateAll(Collection<T> actions);
    }

    private final Host host;
    private State state = new Shared();

    public IsolatedProjectActions(Host host) {
        this.host = host;
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }

    public void onBeforeProject(IsolatedAction<? super Project> action) {
        state.onBeforeProject(action);
    }

    public ProjectEvaluationListener isolate() {
        Isolated isolated = state.isolate(host);
        state = isolated;
        return isolated;
    }

    public void clear() {
        state = new Shared();
    }

    static abstract class State {
        abstract boolean isEmpty();

        abstract Isolated isolate(Host host);

        abstract void onBeforeProject(IsolatedAction<? super Project> action);
    }

    static class Shared extends State {
        @Nullable
        ReferenceOpenHashSet<IsolatedAction<? super Project>> actions = null;

        @Override
        boolean isEmpty() {
            return actions == null;
        }

        @Override
        Isolated isolate(Host host) {
            Preconditions.checkNotNull(actions, "Cannot isolate empty set of actions!");
            return new Isolated(host.isolateAll(actions));
        }

        @Override
        void onBeforeProject(IsolatedAction<? super Project> action) {
            if (actions == null) {
                actions = new ReferenceOpenHashSet<>();
            }
            actions.add(action);
        }
    }

    static class Isolated extends State implements ProjectEvaluationListener {

        private final ImmutableList<Isolatable<IsolatedAction<? super Project>>> actions;

        public Isolated(ImmutableList<Isolatable<IsolatedAction<? super Project>>> actions) {
            this.actions = actions;
        }

        @Override
        public void beforeEvaluate(Project project) {
            for (Isolatable<IsolatedAction<? super Project>> action : actions) {
                action.isolate().execute(project);
            }
        }

        @Override
        public void afterEvaluate(Project project, ProjectState state) {
        }

        @Override
        boolean isEmpty() {
            return false;
        }

        @Override
        Isolated isolate(Host host) {
            throw new IllegalStateException("The set of isolated actions has already been isolated!");
        }

        @Override
        void onBeforeProject(IsolatedAction<? super Project> action) {
            throw new IllegalStateException("Cannot add more isolated actions.");
        }
    }

}
