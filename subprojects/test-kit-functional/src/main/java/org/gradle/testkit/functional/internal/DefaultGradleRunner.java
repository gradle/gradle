/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional.internal;

import org.gradle.api.Action;
import org.gradle.testkit.functional.BuildResult;
import org.gradle.testkit.functional.GradleRunner;
import org.gradle.testkit.functional.UnexpectedBuildFailure;
import org.gradle.testkit.functional.UnexpectedBuildSuccess;
import org.gradle.testkit.functional.internal.dist.GradleDistribution;

public class DefaultGradleRunner extends GradleRunner {
    private final GradleDistribution gradleDistribution;

    public DefaultGradleRunner(GradleDistribution gradleDistribution) {
        this.gradleDistribution = gradleDistribution;
    }

    public BuildResult succeeds() {
        return run(new Action<RuntimeException>() {
            public void execute(RuntimeException exception) {
                if(exception != null) {
                    throw new UnexpectedBuildFailure("Unexpected build execution failure", exception);
                }
            }
        });
    }

    public BuildResult fails() {
        return run(new Action<RuntimeException>() {
            public void execute(RuntimeException exception) {
                if(exception == null) {
                    throw new UnexpectedBuildSuccess("Unexpected build execution success", exception);
                }
            }
        });
    }

    private BuildResult run(Action<RuntimeException> action) {
        GradleExecutor gradleExecutor = new ToolingApiGradleExecutor(gradleDistribution, getWorkingDir());
        gradleExecutor.withGradleUserHomeDir(getGradleUserHomeDir());
        gradleExecutor.withArguments(getArguments());
        gradleExecutor.withTasks(getTasks());
        GradleExecutionHandle gradleExecutionHandle = gradleExecutor.run();
        action.execute(gradleExecutionHandle.getException());
        return new DefaultBuildResult(gradleExecutionHandle.getStandardOutput(), gradleExecutionHandle.getStandardError());
    }
}
