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

package org.gradle.internal.isolation.actions;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;

/**
 * Isolates actions from the project state, ensuring actions do not capture state from the project.
 */
@NonNullApi
public interface ProjectActionIsolator {

    /**
     * Attempts to isolate the given action from a project. If so, return the isolated action.
     * <p>
     * If the action cannot be isolated, emit a deprecation warning and return an action that
     * guards the original action while holding the project lock.
     */
    <T> ThreadSafeAction<T> isolateLenient(Action<T> action, String actionType);

}
