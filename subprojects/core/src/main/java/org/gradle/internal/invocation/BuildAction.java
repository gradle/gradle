/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.invocation;

import org.gradle.api.internal.StartParameterInternal;

/**
 * An object that describes the top level build action to perform, e.g. run some tasks, build a tooling model, run some tests, etc.
 */
public interface BuildAction {
    StartParameterInternal getStartParameter();

    /**
     * Will this action result in tasks being run?
     *
     * <p>An action may both run tasks and create a model.</p>
     */
    boolean isRunTasks();

    /**
     * Will this action return a tooling model as its result?
     *
     * <p>An action may both run tasks and create a model.</p>
     */
    boolean isCreateModel();
}
