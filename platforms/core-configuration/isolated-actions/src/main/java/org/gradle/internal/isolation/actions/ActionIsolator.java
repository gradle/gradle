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
import org.gradle.internal.model.ModelContainer;

/**
 * Isolates actions from their owning context, ensuring actions do not capture state from that context.
 */
@NonNullApi
public interface ActionIsolator {

    /**
     * Attempts to isolate the given action from its owning context. If so, return the isolated action.
     * <p>
     * If the action cannot be isolated, emit a deprecation warning and return an action that is guarded by the
     * lock owned by the given model.
     */
    <T> Action<T> isolateLenient(Action<T> action, String actionType, ModelContainer<?> model);

}
