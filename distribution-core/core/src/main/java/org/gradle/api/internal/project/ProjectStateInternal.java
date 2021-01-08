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

package org.gradle.api.internal.project;

import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectState;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the lifecycle state of a project, with regard to configuration.
 *
 * There are three synonymous terms mixed in here (configure, evaluate, execute) for legacy reasons.
 * Where not bound to backwards compatibility constraints, we use the term “configure”.
 *
 * @see org.gradle.configuration.project.LifecycleProjectEvaluator
 */
public class ProjectStateInternal implements ProjectState {

    enum State {
        UNCONFIGURED,
        IN_BEFORE_EVALUATE,
        IN_EVALUATE,
        IN_AFTER_EVALUATE,
        CONFIGURED
    }

    private State state = State.UNCONFIGURED;
    private ProjectConfigurationException failure;

    @Override
    public boolean getExecuted() {
        // We intentionally consider “execution” done before doing afterEvaluate.
        // The Android plugin relies on this behaviour.
        return state.ordinal() > State.IN_EVALUATE.ordinal();
    }

    public boolean isConfiguring() {
        // Intentionally asymmetrical to getExecuted()
        // This prevents recursion on `project.afterEvaluate { project.evaluate() }`
        return state == State.IN_BEFORE_EVALUATE || state == State.IN_EVALUATE || state == State.IN_AFTER_EVALUATE;
    }

    public boolean isUnconfigured() {
        return state == State.UNCONFIGURED;
    }

    public boolean hasCompleted() {
        return state == State.CONFIGURED;
    }

    public void toBeforeEvaluate() {
        assert state == State.UNCONFIGURED;
        state = State.IN_BEFORE_EVALUATE;
    }

    public void toEvaluate() {
        assert state == State.IN_BEFORE_EVALUATE;
        state = State.IN_EVALUATE;
    }

    public void toAfterEvaluate() {
        assert state == State.IN_EVALUATE;
        state = State.IN_AFTER_EVALUATE;
    }

    public void configured() {
        assert state != State.CONFIGURED;
        state = State.CONFIGURED;
    }

    public void failed(ProjectConfigurationException failure) {
        if (this.failure == null) {
            this.failure = failure;
        } else {
            List<Throwable> causes = new ArrayList<Throwable>(this.failure.getCauses());
            CollectionUtils.addAll(causes, failure.getCauses());
            this.failure.initCauses(causes);
        }
    }

    public boolean hasFailure() {
        return failure != null;
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public void rethrowFailure() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public String toString() {
        String state;

        if (isConfiguring()) {
            state = "EXECUTING";
        } else if (getExecuted()) {
            if (failure == null) {
                state = "EXECUTED";
            } else {
                state = String.format("FAILED (%s)", failure.getMessage());
            }
        } else {
            state = "NOT EXECUTED";
        }

        return String.format("project state '%s'", state);
    }
}
