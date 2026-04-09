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
import org.gradle.api.specs.Spec;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Tracks the application of user code, tracking the total time spent executing each application.
 */
@ServiceScope(Scope.CrossBuildSession.class)
public interface UserCodeApplicationContext {

    /**
     * Applies some user code, assigning an ID for this particular application.
     *
     * @param source The source of the code being applied.
     * @param projectIdentityPath The identity path of the project that user code is being applied to.
     * @param action The action to run to apply the user code.
     */
    void apply(
        UserCodeSource source,
        @Nullable Path projectIdentityPath,
        Action<? super UserCodeApplicationId> action
    );

    /**
     * Runs some Gradle runtime code.
     */
    void gradleRuntime(Runnable runnable);

    /**
     * Returns a handle to the current application, or null if no
     * application is currently being applied on this thread.
     */
    @Nullable Application current();

    /**
     * Captures the current application and returns an {@link Action} that, when invoked,
     * will run the given action within the context of that application. If there is no
     * current application, returns the original action unchanged.
     */
    <T> Action<T> reapplyActionLaterForCurrent(Action<T> action, CodeType codeType);

    /**
     * Captures the current application and returns a {@link Spec} that, when invoked,
     * will run the given spec within the context of that application. If there is no
     * current application, returns the original spec unchanged.
     */
    <T> Spec<T> reapplySpecLaterForCurrent(Spec<T> spec, CodeType codeType);

    /**
     * Representation of the application of some user code. Tracks the amount of time spent
     * executing the application.
     */
    interface Application {

        /**
         * The ID of the application.
         */
        UserCodeApplicationId getId();

        /**
         * Returns details describing the source of the user code, if known.
         */
        @Nullable UserCodeSource getSource();

        /**
         * Executes code owned by this application. While the code is running, and while no other
         * nested calls to any reapply method are made, {@link #current()} will return this application
         * and any time spent executing the application will be tracked.
         * <p>
         * All code executed with the same {@link CodeType} will be tracked together,
         * with the accumulated time accessible via {@link #getDurationNsForType(CodeType)}.
         */
        void reapply(Runnable runnable, CodeType type);

        /**
         * {@link #reapply(Runnable, CodeType)}, but accepts a {@link Supplier}.
         */
        <T> T reapply(Supplier<T> action, CodeType type);

        /**
         * {@link #reapply(Runnable, CodeType)}, but accepts a {@link Spec}.
         */
        <T> boolean reapplySpec(Spec<T> spec, T param, CodeType type);

        /**
         * {@link #reapply(Runnable, CodeType)}, but accepts an {@link Action}.
         */
        <T> void reapplyAction(Action<T> action, T param, CodeType type);

        /**
         * Get the total time spent executing this application, in nanoseconds.
         */
        long getTotalDurationNs();

        /**
         * Get the time spent executing code of the given {@link CodeType}, in nanoseconds.
         */
        long getDurationNsForType(CodeType codeType);

    }

    /**
     * The type of code being executed.
     */
    enum CodeType {

        /**
         * Code that does not belong to any other type.
         */
        GENERAL,

        /**
         * Callbacks executed against a domain object collection.
         */
        COLLECTION_CALLBACK,

        /**
         * Asynchronous listener callbacks.
         */
        LISTENER

    }

}
