/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.logging;

import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class GroupedTaskFixture {

    private final String taskName;

    private String taskOutcome;

    private final List<String> outputs = new ArrayList<String>(1);

    public GroupedTaskFixture(String taskName) {
        this.taskName = taskName;
    }

    protected void addOutput(String output) {
        outputs.add(output);
    }

    public void setOutcome(String taskOutcome) {
        if (this.taskOutcome != null) {
            throw new AssertionError(taskName + " task's outcome is set twice!");
        }
        this.taskOutcome = taskOutcome;
    }

    public String getOutcome(){
        return taskOutcome;
    }

    public String getName() {
        return taskName;
    }

    public String getOutput() {
        return CollectionUtils.join("\n", outputs);
    }

    public List<String> getOutputs() {
        return outputs;
    }
}
