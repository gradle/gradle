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

package org.gradle.testkit.runner.internal;

import org.gradle.api.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.internal.feature.BuildResultOutputFeatureCheck;
import org.gradle.testkit.runner.internal.feature.BuildResultTasksFeatureCheck;
import org.gradle.testkit.runner.internal.feature.FeatureCheck;

import java.util.List;

public class FeatureCheckBuildResult implements BuildResult {

    private final BuildResult delegateBuildResult;
    private final FeatureCheck outputFeatureCheck;
    private final FeatureCheck tasksFeatureCheck;

    public FeatureCheckBuildResult(BuildOperationParameters buildOperationParameters, String output, List<BuildTask> tasks) {
        delegateBuildResult = new DefaultBuildResult(output, tasks);
        outputFeatureCheck = new BuildResultOutputFeatureCheck(buildOperationParameters.getTargetGradleVersion(), buildOperationParameters.isEmbedded());
        tasksFeatureCheck = new BuildResultTasksFeatureCheck(buildOperationParameters.getTargetGradleVersion());
    }

    @Override
    public String getOutput() {
        outputFeatureCheck.verify();
        return delegateBuildResult.getOutput();
    }

    @Override
    public List<BuildTask> getTasks() {
        tasksFeatureCheck.verify();
        return delegateBuildResult.getTasks();
    }

    @Override
    public List<BuildTask> tasks(TaskOutcome outcome) {
        tasksFeatureCheck.verify();
        return delegateBuildResult.tasks(outcome);
    }

    @Override
    public List<String> taskPaths(TaskOutcome outcome) {
        tasksFeatureCheck.verify();
        return delegateBuildResult.taskPaths(outcome);
    }

    @Nullable
    @Override
    public BuildTask task(String taskPath) {
        tasksFeatureCheck.verify();
        return delegateBuildResult.task(taskPath);
    }
}
