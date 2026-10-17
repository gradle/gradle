/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.execution.plan.ActionNode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A node that fails when executed, without doing any work. Its failure is a node failure, like a task failure, so under
 * {@code --continue} only the nodes depending on it are skipped and the rest of the plan keeps running.
 */
@NullMarked
class BrokenBuildTaskNode extends ActionNode {
    private final GradleException failure;
    private boolean executed;

    BrokenBuildTaskNode(BrokenBuildTaskDependency dependency) {
        super(new NoOpAction(dependency));
        this.failure = new GradleException(String.format("Cannot run task '%s' of build '%s' because the build failed to configure.", dependency.getTaskPath(), dependency.getBuildName()));
    }

    @Override
    public void execute(NodeExecutionContext context) {
        super.execute(context);
        executed = true;
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return executed ? failure : null;
    }

    private static class NoOpAction implements WorkNodeAction {
        private final BrokenBuildTaskDependency dependency;

        NoOpAction(BrokenBuildTaskDependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public void run(NodeExecutionContext context) {
        }

        @Override
        public String toString() {
            return String.format("task '%s' of broken build '%s'", dependency.getTaskPath(), dependency.getBuildName());
        }
    }
}
