/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.GradleScriptException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Build;

public class DefaultProjectEvaluator implements ProjectEvaluator, BuildListener {
    private ProjectEvaluationListener listener;
    private final ProjectEvaluator[] evaluators;

    public DefaultProjectEvaluator(ProjectEvaluator... evaluators) {
        this.evaluators = evaluators;
    }

    public void evaluate(ProjectInternal project) {
        listener.beforeEvaluate(project);
        GradleScriptException failure = null;
        try {
            for (ProjectEvaluator evaluator : evaluators) {
                evaluator.evaluate(project);
            }
        } catch (Throwable t) {
            failure = new GradleScriptException(String.format("A problem occurred evaluating %s.", project), t,
                    project.getBuildScriptSource());
        }
        listener.afterEvaluate(project, failure);
        if (failure != null) {
            throw failure;
        }
    }

    public void buildStarted(StartParameter startParameter) {
    }

    public void settingsEvaluated(Settings settings) {
    }

    public void projectsLoaded(Build build) {
        listener = ((BuildInternal) build).getProjectEvaluationBroadcaster();
    }

    public void projectsEvaluated(Build build) {
    }

    public void taskGraphPopulated(TaskExecutionGraph graph) {
    }

    public void buildFinished(BuildResult result) {
    }
}
