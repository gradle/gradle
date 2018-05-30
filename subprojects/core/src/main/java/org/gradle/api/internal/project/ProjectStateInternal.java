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

import org.gradle.api.ProjectState;
import org.gradle.internal.UncheckedException;

public class ProjectStateInternal implements ProjectState {

    enum State {
        NOT_EXECUTED,
        EXECUTING,
        EXECUTED
    }

    private State state = State.NOT_EXECUTED;
    private Throwable failure;

    @Override
    public boolean getExecuted() {
        return state == State.EXECUTED;
    }

    public boolean getNotExecuted() {
        return state == State.NOT_EXECUTED;
    }

    public boolean getExecuting() {
        return state == State.EXECUTING;
    }

    public void executing() {
        state = State.EXECUTING;
    }

    public void executed() {
        state = State.EXECUTED;
    }

    public void executed(Throwable failure) {
        assert this.failure == null;
        this.failure = failure;
        executed();
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
        if (failure == null) {
            return;
        }
        throw UncheckedException.throwAsUncheckedException(failure);
    }

    @Override
    public String toString() {
        String state;

        if (getExecuting()) {
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
