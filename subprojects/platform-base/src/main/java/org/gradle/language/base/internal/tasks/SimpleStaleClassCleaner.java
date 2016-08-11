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
package org.gradle.language.base.internal.tasks;

import org.gradle.api.internal.TaskOutputsInternal;

import java.io.File;

public class SimpleStaleClassCleaner implements StaleClassCleaner {
    private final TaskOutputsInternal taskOutputs;
    private final String propertyName;
    private boolean didWork;

    public SimpleStaleClassCleaner(TaskOutputsInternal taskOutputs, String propertyName) {
        this.taskOutputs = taskOutputs;
        this.propertyName = propertyName;
    }

    @Override
    public void execute() {
        for (File previousOutputFile : taskOutputs.getPreviousOutputFiles(propertyName)) {
            didWork |= previousOutputFile.delete();
        }
    }

    public boolean getDidWork() {
        return didWork;
    }
}
