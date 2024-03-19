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

import com.google.common.io.ByteSource;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.internal.feature.BuildResultOutputFeatureCheck;
import org.gradle.testkit.runner.internal.feature.FeatureCheck;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.List;

public class FeatureCheckBuildResult implements BuildResult {

    private final BuildResult delegateBuildResult;
    private final FeatureCheck outputFeatureCheck;

    public FeatureCheckBuildResult(BuildOperationParameters buildOperationParameters, @Nonnull String output, List<BuildTask> tasks) {
        this(buildOperationParameters, ByteSource.wrap(output.getBytes(Charset.defaultCharset())), tasks);
    }

    public FeatureCheckBuildResult(
        BuildOperationParameters buildOperationParameters,
        @Nonnull ByteSource outputSource,
        List<BuildTask> tasks
    ) {
        delegateBuildResult = new DefaultBuildResult(outputSource, tasks);
        outputFeatureCheck = new BuildResultOutputFeatureCheck(buildOperationParameters.getTargetGradleVersion(), buildOperationParameters.isEmbedded());
    }

    @Override
    public String getOutput() {
        outputFeatureCheck.verify();
        return delegateBuildResult.getOutput();
    }

    @Override
    public List<BuildTask> getTasks() {
        return delegateBuildResult.getTasks();
    }

    @Override
    public List<BuildTask> tasks(TaskOutcome outcome) {
        return delegateBuildResult.tasks(outcome);
    }

    @Override
    public List<String> taskPaths(TaskOutcome outcome) {
        return delegateBuildResult.taskPaths(outcome);
    }

    @Nullable
    @Override
    public BuildTask task(String taskPath) {
        return delegateBuildResult.task(taskPath);
    }
}
