/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.execution.model.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.properties.annotations.AbstractFunctionAnnotationHandler;

/**
 * Handles the {@link TaskAction} annotation.
 */
public class TaskActionAnnotationHandler extends AbstractFunctionAnnotationHandler {
    public TaskActionAnnotationHandler() {
        super(TaskAction.class, ImmutableSet.of());
    }
}
