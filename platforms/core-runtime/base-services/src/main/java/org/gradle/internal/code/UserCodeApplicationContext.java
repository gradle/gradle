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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Supplier;

/**
 * Tracks the application of user code, tracking the total time spent executing each application.
 */
@ThreadSafe
@ServiceScope(Scope.CrossBuildSession.class)
public interface UserCodeApplicationContext {

    /**
     * Begins recording user code applications. All {@link #apply} actions executed on all
     * threads are recorded until the returned recording is stopped.
     *
     * @throws IllegalStateException If a recording is already in progress.
     */
    Recording startRecording();

    /**
     * Applies some user code from the given source to the given target, tracking the time spent
     * executing that code.
     *
     * @param source The source of the code being applied.
     * @param target The target that user code is being applied to.
     * @param action The action to run to apply the user code.
     */
    void apply(
        UserCodeSource source,
        Target target,
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
     * Get all applications applied to the given target.
     * <p>
     * New applications applied while this method is executing may not
     * be included in the returned list.
     *
     * @throws IllegalStateException If recording is not in progress.
     */
    ImmutableList<Application> getApplicationsFor(Target target);

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
         * Returns details describing the source of the user code.
         */
        UserCodeSource getSource();

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
        <T> T reapplySupplier(Supplier<T> action, CodeType type);

        /**
         * {@link #reapply(Runnable, CodeType)}, but accepts a {@link Spec}.
         */
        <T> boolean reapplySpec(Spec<T> spec, T param, CodeType type);

        /**
         * {@link #reapply(Runnable, CodeType)}, but accepts an {@link Action}.
         */
        <T> void reapplyAction(Action<T> action, T param, CodeType type);

        /**
         * Get a snapshot of the total time spent executing this application, in nanoseconds.
         */
        long getTotalDurationNs();

        /**
         * Get a snapshot of the time spent executing code of the given {@link CodeType}, in nanoseconds.
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

    /**
     * A recording of user code applications, started by {@link #startRecording()}.
     */
    interface Recording {

        /**
         * Stops this recording and returns all applications that occurred while it
         * was in progress, mapped by the target they were applied to.
         *
         * @throws IllegalStateException If this recording is not the recording in progress.
         */
        ImmutableMap<Target, ImmutableList<Application>> stop();

    }

    /**
     * A target of some user code application.
     */
    interface Target {

        /**
         * A target modeling a Gradle Project.
         */
        class Project implements Target {

            public final Path projectIdentityPath;

            public Project(Path projectIdentityPath) {
                this.projectIdentityPath = projectIdentityPath;
            }

            @Override
            public boolean equals(@Nullable Object o) {
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                Project that = (Project) o;
                return projectIdentityPath.equals(that.projectIdentityPath);
            }

            @Override
            public int hashCode() {
                return projectIdentityPath.hashCode();
            }

        }

        /**
         * A target modeling some non-project gradle domain.
         */
        class Other implements Target {

            public static final Other INSTANCE = new Other();

            private Other() {
            }

        }

    }

}
