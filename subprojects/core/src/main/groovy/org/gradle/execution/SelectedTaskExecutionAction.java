/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.AbstractMultiCauseException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.util.UncheckedException;

import java.util.ArrayList;
import java.util.List;

public class SelectedTaskExecutionAction implements BuildExecutionAction {
    public void execute(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        TaskGraphExecuter taskGraph = gradle.getTaskGraph();
        if (gradle.getStartParameter().isContinueOnFailure()) {
            MultipleFailuresHandler handler = new MultipleFailuresHandler();
            taskGraph.useFailureHandler(handler);
            taskGraph.execute();
            handler.rethrowFailures();
        } else {
            taskGraph.execute();
        }
    }

    private static class MultipleFailuresHandler implements TaskFailureHandler {
        final List<Throwable> failures = new ArrayList<Throwable>();
        
        public void onTaskFailure(Task task) {
            failures.add(task.getState().getFailure());
        }

        public void rethrowFailures() {
            if (failures.isEmpty()) {
                return;
            }
            if (failures.size() == 1) {
                throw UncheckedException.asUncheckedException(failures.get(0));
            } else {
                throw new AbstractMultiCauseException("Multiple tasks failed.", failures);
            }
        }
    }
}
