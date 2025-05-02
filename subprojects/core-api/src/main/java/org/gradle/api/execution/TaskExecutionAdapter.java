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
package org.gradle.api.execution;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;

/**
 * A {@link TaskExecutionListener} adapter class for receiving task execution events.
 *
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
@SuppressWarnings("deprecation")
public class TaskExecutionAdapter implements TaskExecutionListener {

    @Override
    public void beforeExecute(Task task) {}

    @Override
    public void afterExecute(Task task, TaskState state) {}

}
