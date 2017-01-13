/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.launcher.exec;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.invocation.BuildController;

public class GradleBuildController implements BuildController {
    private enum State {Created, Completed}

    private State state = State.Created;
    private boolean hasResult;
    private Object result;
    private final GradleLauncher gradleLauncher;

    public GradleBuildController(GradleLauncher gradleLauncher) {
        this.gradleLauncher = gradleLauncher;
    }

    public GradleLauncher getLauncher() {
        if (state == State.Completed) {
            throw new IllegalStateException("Cannot use launcher after build has completed.");
        }
        return gradleLauncher;
    }

    @Override
    public void addNestedListener(Object listener) {
        gradleLauncher.addNestedListener(listener);
    }

    @Override
    public boolean hasResult() {
        return hasResult;
    }

    @Override
    public Object getResult() {
        if (!hasResult) {
            throw new IllegalStateException("No result has been provided for this build action.");
        }
        return result;
    }

    @Override
    public void setResult(Object result) {
        this.hasResult = true;
        this.result = result;
    }

    public GradleInternal getGradle() {
        return getLauncher().getGradle();
    }

    public GradleInternal run() {
        try {
            return (GradleInternal) getLauncher().run().getGradle();
        } finally {
            state = State.Completed;
        }
    }

    public GradleInternal configure() {
        try {
            return (GradleInternal) getLauncher().getBuildAnalysis().getGradle();
        } finally {
            state = State.Completed;
        }
    }
}
