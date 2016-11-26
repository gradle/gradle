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
package org.gradle.configuration.project;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages lifecycle concerns while delegating actual evaluation to another evaluator
 */
public class LifecycleProjectEvaluator implements ProjectEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleProjectEvaluator.class);

    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectEvaluator delegate;

    public LifecycleProjectEvaluator(BuildOperationExecutor buildOperationExecutor, ProjectEvaluator delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    public void evaluate(final ProjectInternal project, final ProjectStateInternal state) {
        if (state.getExecuted() || state.getExecuting()) {
            return;
        }

        String displayName = "project " + project.getIdentityPath().toString();
        buildOperationExecutor.run(BuildOperationDetails.displayName("Configure " + displayName).name(StringUtils.capitalize(displayName)).build(), new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                doConfigure(project, state);
                state.rethrowFailure();
            }
        });
    }

    private void doConfigure(ProjectInternal project, ProjectStateInternal state) {
        ProjectEvaluationListener listener = project.getProjectEvaluationBroadcaster();
        try {
            listener.beforeEvaluate(project);
        } catch (Exception e) {
            addConfigurationFailure(project, state, e);
            return;
        }

        state.setExecuting(true);
        try {
            delegate.evaluate(project, state);
        } catch (Exception e) {
            addConfigurationFailure(project, state, e);
        } finally {
            state.setExecuting(false);
            state.executed();
            notifyAfterEvaluate(listener, project, state);
        }
    }

    private void notifyAfterEvaluate(ProjectEvaluationListener listener, ProjectInternal project, ProjectStateInternal state) {
        try {
            listener.afterEvaluate(project, state);
        } catch (Exception e) {
            if (state.hasFailure()) {
                // Just log this failure, and pass the existing failure out in the project state
                LOGGER.error("Failed to notify ProjectEvaluationListener.afterEvaluate(), but primary configuration failure takes precedence.", e);
                return;
            }
            addConfigurationFailure(project, state, e);
        }
    }

    private void addConfigurationFailure(ProjectInternal project, ProjectStateInternal state, Exception e) {
        ProjectConfigurationException failure = new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project.getDisplayName()), e);
        state.executed(failure);
    }
}
