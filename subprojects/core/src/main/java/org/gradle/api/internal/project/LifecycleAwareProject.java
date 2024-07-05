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

package org.gradle.api.internal.project;

import org.gradle.invocation.EagerLifecycleExecutor;


public class LifecycleAwareProject extends MutableStateAccessAwareProject {

    public static ProjectInternal from(
        ProjectInternal project,
        EagerLifecycleExecutor eagerLifecycleExecutor
    ) {
        return project instanceof LifecycleAwareProject
            ? project
            : new LifecycleAwareProject(project, eagerLifecycleExecutor);
    }

    private final EagerLifecycleExecutor eagerLifecycleExecutor;

    public LifecycleAwareProject(
        ProjectInternal delegate,
        EagerLifecycleExecutor eagerLifecycleExecutor
    ) {
        super(delegate);
        this.eagerLifecycleExecutor = eagerLifecycleExecutor;
    }

    @Override
    protected void onMutableStateAccess(String what) {
        eagerLifecycleExecutor.executeAllprojectsFor(delegate);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (other instanceof LifecycleAwareProject) {
            LifecycleAwareProject lifecycleAwareProject = (LifecycleAwareProject) other;
            return delegate.equals(lifecycleAwareProject.delegate);
        } else {
            return delegate.equals(other);
        }
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
