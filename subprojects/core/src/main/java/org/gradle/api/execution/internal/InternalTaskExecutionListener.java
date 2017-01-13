/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.execution.internal;

import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;

/**
 * Used by build scans to collect some task details, and will be retired. Use {@link org.gradle.internal.progress.InternalBuildListener} instead.
 */
public interface InternalTaskExecutionListener {
    void beforeExecute(TaskOperationInternal taskOperation, OperationStartEvent startEvent);
    void afterExecute(TaskOperationInternal taskOperation, OperationResult result);
}
