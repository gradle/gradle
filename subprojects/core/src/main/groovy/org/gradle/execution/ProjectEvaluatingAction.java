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

import org.gradle.StartParameter;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.List;

/**
 * Ensures that projects resolved from the command line task names are evaluated.
 */
public class ProjectEvaluatingAction implements BuildConfigurationAction {

    private final TaskPathProjectEvaluator evaluator;

    public ProjectEvaluatingAction() {
        this(new TaskPathProjectEvaluator());
    }

    public ProjectEvaluatingAction(TaskPathProjectEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void configure(BuildExecutionContext context) {
        StartParameter param = context.getGradle().getStartParameter();
        List<String> taskNames = param.getTaskNames();
        ProjectInternal project = context.getGradle().getDefaultProject();

        if (param.getTaskNames().isEmpty()) {
            //so that we don't miss out default tasks
            project.evaluate();
        }

        for (String path : taskNames) {
            evaluator.evaluateByPath(project, path);
        }
        context.proceed();
    }
}