/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.configuration;

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.internal.configuration.LifecycleListenerExecutionBuildOperationType.DetailsImpl;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class BuildOperationEmittingProjectEvaluationListener implements ProjectEvaluationListener, ListenerDelegate {
    private final ProjectEvaluationListener delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final long parentBuildOperationId;

    public BuildOperationEmittingProjectEvaluationListener(ProjectEvaluationListener delegate, BuildOperationExecutor buildOperationExecutor, long parentBuildOperationId) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.parentBuildOperationId = parentBuildOperationId;
    }

    @Override
    public void beforeEvaluate(Project project) {
        delegate.beforeEvaluate(project);
    }

    @Override
    public void afterEvaluate(final Project project, final ProjectState state) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                delegate.afterEvaluate(project, state);
                context.setResult(LifecycleListenerExecutionBuildOperationType.RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return new DetailsImpl(parentBuildOperationId).desc();
            }
        });
    }

    @Override
    public Object getDelegate() {
        return getDelegate();
    }
}
