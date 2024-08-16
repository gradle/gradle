/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;

public class TaskPathProjectEvaluator implements ProjectConfigurer {
    private final BuildCancellationToken cancellationToken;
    private final BuildOperationExecutor buildOperationExecutor;

    public TaskPathProjectEvaluator(BuildCancellationToken cancellationToken, BuildOperationExecutor buildOperationExecutor) {
        this.cancellationToken = cancellationToken;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void configure(ProjectInternal project) {
        project.getOwner().ensureConfigured();
    }

    @Override
    public void configureFully(ProjectState projectState) {
        projectState.ensureConfigured();
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException();
        }
        projectState.ensureTasksDiscovered();
    }

    @Override
    public void configureHierarchy(ProjectInternal project) {
        configure(project);
        for (Project sub : project.getSubprojects()) {
            configure((ProjectInternal) sub);
        }
    }

    @Override
    public void configureHierarchyInParallel(ProjectInternal project) {
        try {
            buildOperationExecutor.runAllWithAccessToProjectState(queue -> {
                for (Project p : project.getAllprojects()) {
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) {
                            configure((ProjectInternal) p);
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("Configure project " + p.getName());
                        }
                    });
                }
            });
        } catch (MultipleBuildOperationFailures e) {
            if (e.getCauses().size() == 1) {
                throw UncheckedException.throwAsUncheckedException(e.getCauses().get(0));
            }
            throw e;
        }
    }
}
