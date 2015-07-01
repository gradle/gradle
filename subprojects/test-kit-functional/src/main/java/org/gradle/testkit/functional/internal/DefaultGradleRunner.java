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

import org.apache.commons.lang.exception.ExceptionUtils;
import org.gradle.api.Action;
import org.gradle.internal.SystemProperties;
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
        return run(new Action<GradleExecutionResult>() {
            public void execute(GradleExecutionResult gradleExecutionResult) {
                if(!gradleExecutionResult.isSuccessful()) {
                    throw new UnexpectedBuildFailure(createDiagnosticsMessage("Unexpected build execution failure", gradleExecutionResult));
                }
            }
        });
    }

    public BuildResult fails() {
        return run(new Action<GradleExecutionResult>() {
            public void execute(GradleExecutionResult gradleExecutionResult) {
                if(gradleExecutionResult.isSuccessful()) {
                    throw new UnexpectedBuildSuccess(createDiagnosticsMessage("Unexpected build execution success", gradleExecutionResult));
                }
            }
        });
    }

    private String createDiagnosticsMessage(String trailingMessage, GradleExecutionResult gradleExecutionResult) {
        String lineBreak = SystemProperties.getInstance().getLineSeparator();
        StringBuilder message = new StringBuilder();
        message.append(trailingMessage);
        message.append(" in ");
        message.append(getWorkingDir().getAbsolutePath());
        message.append(" with tasks ");
        message.append(getTasks());
        message.append(" and arguments ");
        message.append(getArguments());
        message.append(lineBreak).append(lineBreak);
        message.append("Output:");
        message.append(lineBreak);
        message.append(gradleExecutionResult.getStandardOutput());
        message.append(lineBreak);
        message.append("-----");
        message.append(lineBreak);
        message.append("Error:");
        message.append(lineBreak);
        message.append(gradleExecutionResult.getStandardError());
        message.append(lineBreak);
        message.append("-----");

        if(gradleExecutionResult.getThrowable() != null) {
            message.append(lineBreak);
            message.append("Reason:");
            message.append(lineBreak);
            message.append(determineExceptionMessage(gradleExecutionResult.getThrowable()));
            message.append(lineBreak);
            message.append("-----");
        }

        return message.toString();
    }

    private String determineExceptionMessage(Throwable throwable) {
        return throwable.getCause() == null ? throwable.getMessage() : ExceptionUtils.getRootCause(throwable).getMessage();
    }

    private BuildResult run(Action<GradleExecutionResult> action) {
        GradleExecutor gradleExecutor = new ToolingApiGradleExecutor(gradleDistribution, getWorkingDir());
        gradleExecutor.withGradleUserHomeDir(getGradleUserHomeDir());
        gradleExecutor.withArguments(getArguments());
        gradleExecutor.withTasks(getTasks());
        GradleExecutionResult gradleExecutionResult = gradleExecutor.run();
        action.execute(gradleExecutionResult);
        return new DefaultBuildResult(gradleExecutionResult.getStandardOutput(), gradleExecutionResult.getStandardError(),
                                      gradleExecutionResult.getExecutedTasks(), gradleExecutionResult.getSkippedTasks());
    }
}
