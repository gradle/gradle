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

import org.gradle.api.GradleException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;

public class DefaultProjectEvaluator implements ProjectEvaluator {
    private final ProjectEvaluator[] evaluators;

    public DefaultProjectEvaluator(ProjectEvaluator... evaluators) {
        this.evaluators = evaluators;
    }

    public void evaluate(ProjectInternal project) {
        ProjectEvaluationListener listener = project.getGradle().getProjectEvaluationBroadcaster();
        listener.beforeEvaluate(project);
        Throwable failure = null;
        try {
            for (ProjectEvaluator evaluator : evaluators) {
                evaluator.evaluate(project);
            }
        } catch (GradleException t) {
            failure = t;
        }
        listener.afterEvaluate(project, failure);
        if (failure != null) {
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new GradleException(failure);
        }
    }
}
