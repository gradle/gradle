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

package org.gradle.internal.progress;

/**
 * Enumerates the build operations tracked by the progress event infrastructure.
 */
public enum BuildOperationType {

    RUNNING_BUILD("Run build"),
    EVALUATING_INIT_SCRIPTS("Run init scripts"),
    EVALUATING_SETTINGS("Load projects"),
    CONFIGURING_BUILD("Configure build"),
    POPULATING_TASK_GRAPH("Calculate task graph"),
    EXECUTING_TASKS("Run tasks");

    private String name;

    BuildOperationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return getName();
    }

}
