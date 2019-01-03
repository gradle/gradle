/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.configuration.internal;

import org.gradle.api.Action;

import javax.annotation.Nullable;

/**
 * Assigns and stores an ID for the application of some user code (e.g. scripts and plugins).
 */
public interface UserCodeApplicationContext {

    void apply(Action<? super UserCodeApplicationId> action);

    void reapply(UserCodeApplicationId id, Runnable runnable);

    <T> Action<T> decorateWithCurrent(Action<T> action);

    @Nullable
    UserCodeApplicationId current();
}
