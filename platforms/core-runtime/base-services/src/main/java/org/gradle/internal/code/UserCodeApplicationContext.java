/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.code;

import org.gradle.api.Action;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Assigns and stores an ID for the application of some user code (e.g. scripts and plugins).
 */
@ServiceScope(Scopes.BuildSession.class)
public interface UserCodeApplicationContext {
    /**
     * Applies some user code, assigning an ID for this particular application.
     *
     * @param source The source of the code being applied.
     * @param action The action to run to apply the user code.
     */
    void apply(UserCodeSource source, Action<? super UserCodeApplicationId> action);

    /**
     * Runs some Gradle runtime code.
     */
    void gradleRuntime(Runnable runnable);

    /**
     * Returns an action that represents some deferred execution of the current user code. While the returned action is running, the details of the current application are restored.
     * Returns the given action when there is no current application.
     */
    <T> Action<T> reapplyCurrentLater(Action<T> action);

    /**
     * Returns details of the current application, if any.
     */
    @Nullable
    Application current();

    /**
     * Immutable representation of the application of some user code.
     */
    interface Application {
        UserCodeApplicationId getId();

        /**
         * Returns details describing the source of the user code.
         */
        UserCodeSource getSource();

        /**
         * Returns an action that represents some deferred execution of the user code. While the returned action is running, the details of this application are restored.
         */
        <T> Action<T> reapplyLater(Action<T> action);

        /**
         * Runs an action that represents some deferred execution of the user code. While the action is running, the details of this application are restored.
         */
        void reapply(Runnable runnable);

        /**
         * Runs an action that represents some deferred execution of the user code. While the action is running, the details of this application are restored.
         */
        <T> T reapply(Supplier<T> action);
    }
}
