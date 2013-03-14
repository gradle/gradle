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

package org.gradle.configuration;

import org.gradle.api.GradleScriptException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;

import java.util.Arrays;
import java.util.List;

public class ConfigureActionsProjectEvaluator implements ProjectEvaluator {
    private final ProjectEvaluator evaluator;
    private final List<ProjectConfigureAction> configureActions;

    public ConfigureActionsProjectEvaluator(ProjectEvaluator evaluator, ProjectConfigureAction... configureActions) {
        this.evaluator = evaluator;
        this.configureActions = Arrays.asList(configureActions);
    }

    public void evaluate(ProjectInternal project, ProjectStateInternal state) {
        evaluator.evaluate(project, state);

        if (state.hasFailure()) {
            return;
        }

        try {
            for (ProjectConfigureAction configureAction : configureActions) {
                configureAction.execute(project);
            }
        } catch (Exception e) {
            // Ensure that we get the same exception as if the extension was configured by use in script itself.
            GradleScriptException failure = new GradleScriptException(String.format("A problem occurred configuring %s.", project), e);
            state.executed(failure);
        }

    }
}
