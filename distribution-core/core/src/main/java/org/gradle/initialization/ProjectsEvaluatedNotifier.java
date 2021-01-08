/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class ProjectsEvaluatedNotifier {
    private static final NotifyProjectsEvaluatedBuildOperationType.Result PROJECTS_EVALUATED_RESULT = new NotifyProjectsEvaluatedBuildOperationType.Result() {
    };
    private final BuildOperationExecutor buildOperationExecutor;

    public ProjectsEvaluatedNotifier(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void notify(GradleInternal gradle) {
        buildOperationExecutor.run(new NotifyProjectsEvaluatedListeners(gradle));
    }

    private static class NotifyProjectsEvaluatedListeners implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public NotifyProjectsEvaluatedListeners(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext context) {
            gradle.getBuildListenerBroadcaster().projectsEvaluated(gradle);
            context.setResult(PROJECTS_EVALUATED_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Notify projectsEvaluated listeners"))
                .details(new NotifyProjectsEvaluatedBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().toString();
                    }
                });
        }
    }
}
