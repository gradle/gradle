/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.BiFunction;

@ServiceScope(Scope.Project.class)
public interface TaskShadowingRegistry {
    <T extends Task> Class<T> getShadowType(Class<T> type);

    <T extends Task> T maybeWrap(T task, Class<T> requestedType);

    <T extends Task> TaskProvider<T> maybeWrapProvider(TaskProvider<T> provider, Class<T> requestedType);

    <T extends Task> TaskCollection<T> maybeWrapCollection(TaskCollection<T> collection, Class<T> requestedType);

    <T extends Task> Action<? super T> maybeWrapAction(Action<? super T> action, Class<T> requestedType);

    /**
     * Registers a shadowing rule.
     *
     * @param publicType the type exposed to the user
     * @param shadowType the internal type used for storage
     * @param wrapper a function that wraps an internal task/provider/collection into a public one
     * @param <T> the public type
     * @param <S> the shadow type
     */
    <T extends Task, S extends Task> void registerShadowing(Class<T> publicType, Class<S> shadowType, BiFunction<Object, Class<T>, Object> wrapper);
}
