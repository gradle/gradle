/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;

public class DefaultProjectEvaluator implements ProjectEvaluator {
    private final ProjectEvaluator evaluator;

    public DefaultProjectEvaluator(ProjectEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void evaluate(ProjectInternal project, ProjectStateInternal state) {
        if (state.getExecuted()) {
            return;
        }

        ProjectEvaluationListener listener = project.getProjectEvaluationBroadcaster();
        listener.beforeEvaluate(project);
        state.setExecuting(true);
        try {
            evaluator.evaluate(project, state);
        } finally {
            state.setExecuting(false);
            state.executed();
            listener.afterEvaluate(project, state);
        }
    }
}
