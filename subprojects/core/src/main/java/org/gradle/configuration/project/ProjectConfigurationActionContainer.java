/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.configuration.project;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * A container responsible for managing the configuration of a project.
 */
public interface ProjectConfigurationActionContainer {
    void finished();

    Iterable<Action<? super ProjectInternal>> getActions();

    /**
     * Registers an action to execute to configure the project. Actions are executed in an arbitrary order.
     */
    void add(Action<? super ProjectInternal> action);

    /**
     * Registers an action to execute to configure the project. Actions are executed in an arbitrary order.
     */
    void add(Closure action);
}
