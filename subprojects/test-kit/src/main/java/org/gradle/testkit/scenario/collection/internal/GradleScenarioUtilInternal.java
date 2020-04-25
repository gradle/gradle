/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario.collection.internal;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;


class GradleScenarioUtilInternal {

    static void assertTaskOutcomes(String step, String[] taskPaths, BuildResult result, TaskOutcome outcome) {
        for (String taskPath : taskPaths) {
            BuildTask task = result.task(taskPath);
            assert task != null
                : "Step '" + step + "': task '" + taskPath + "' didn't run";
            assert task.getOutcome() == outcome
                : "Step '" + step + "': expected task '" + taskPath + "' to be " + outcome + " but was " + task.getOutcome();
        }
    }
}
