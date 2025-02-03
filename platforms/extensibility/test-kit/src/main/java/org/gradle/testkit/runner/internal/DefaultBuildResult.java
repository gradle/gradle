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
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

public class DefaultBuildResult implements BuildResult {

    private final ByteSource outputSource;

    private final List<BuildTask> tasks;

    public DefaultBuildResult(String output, List<BuildTask> tasks) {
        this(ByteSource.wrap(output.getBytes(Charset.defaultCharset())), tasks);
    }

    public DefaultBuildResult(@Nonnull ByteSource outputSource, List<BuildTask> tasks) {
        this.outputSource = outputSource;
        this.tasks = tasks;
    }

    @Override
    public String getOutput() {
        try {
            return outputSource.asCharSource(Charset.defaultCharset()).read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<BuildTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    @Override
    public List<BuildTask> tasks(final TaskOutcome outcome) {
        return Collections.unmodifiableList(CollectionUtils.filter(tasks, element -> element.getOutcome() == outcome));
    }

    @Override
    public List<String> taskPaths(TaskOutcome outcome) {
        return Collections.unmodifiableList(CollectionUtils.collect(tasks(outcome), BuildTask::getPath));
    }

    @Nullable
    @Override
    public BuildTask task(String taskPath) {
        for (BuildTask task : tasks) {
            if (task.getPath().equals(taskPath)) {
                return task;
            }
        }

        return null;
    }

}
