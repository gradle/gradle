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

public interface InternalBuildListener {

    String RUNNING_BUILD_OPERATION = "Running build";
    String EVALUATING_INIT_SCRIPTS_OPERATION = "Evaluating init scripts";
    String EVALUATING_SETTINGS_OPERATION = "Evaluating settings";
    String LOADING_BUILD_OPERATION = "Loading build";
    String CONFIGURING_BUILD_OPERATION = "Configuring build";
    String POPULATING_TASK_GRAPH_OPERATION = "Populating task graph";
    String EXECUTING_TASKS = "Executing tasks";

    void started(BuildOperationInternal source, long startTime, String eventType);

    void finished(BuildOperationInternal source, long startTime, long endTime, String eventType);

}
