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

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskResult;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultBuildResult implements BuildResult {
    private final String standardOutput;
    private final String standardError;
    private final List<BuildTask> tasks;

    public DefaultBuildResult(String standardOutput, String standardError, List<BuildTask> tasks) {
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.tasks = tasks;
    }

    public String getOutput() {
        return standardOutput;
    }

    public String getErrorOutput() {
        return standardError;
    }

    public List<BuildTask> getTasks() {
        return tasks;
    }

    public List<BuildTask> tasks(final TaskResult result) {
        return CollectionUtils.filter(tasks, new Spec<BuildTask>() {
            public boolean isSatisfiedBy(BuildTask element) {
                return element.getResult() == result;
            }
        });
    }

    public List<String> taskPaths(TaskResult result) {
        return CollectionUtils.collect(tasks(result), new Transformer<String, BuildTask>() {
            public String transform(BuildTask buildTask) {
                return buildTask.getPath();
            }
        });
    }
}
