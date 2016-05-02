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
    private boolean executing;
    private boolean executed;
    private Throwable failure;

    public boolean getExecuted() {
        return executed;
    }

    public void executed() {
        executed = true;
    }

    public void executed(Throwable failure) {
        assert this.failure == null;
        this.failure = failure;
        executed = true;
    }

    public boolean getExecuting() {
        return executing;
    }

    public void setExecuting(boolean executing) {
        this.executing = executing;
    }

    public boolean hasFailure() {
        return failure != null;
    }

    public Throwable getFailure() {
        return failure;
    }

    public void rethrowFailure() {
        if (failure == null) {
            return;
        }
        throw UncheckedException.throwAsUncheckedException(failure);
    }
    
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
