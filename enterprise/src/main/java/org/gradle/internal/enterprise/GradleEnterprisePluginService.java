/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise;

import groovy.lang.Closure;
import org.gradle.internal.operations.notify.BuildOperationNotificationListener;

/**
 * A service for a particular build invocation
 *
 * Implemented by the Enterprise plugin.
 */
public interface GradleEnterprisePluginService {

    /**
     * To be registered for notifications by Gradle.
     *
     * It expects to receive notifications about all operations from the very start of the build.
     */
    BuildOperationNotificationListener getBuildOperationNotificationListener();

    /**
     * Used to signal a start of the execution of main tasks of a build tree, also known as <a href="https://docs.gradle.org/current/userguide/build_lifecycle.html#sec:build_phases">the "execution phase"</a>.
     * At this point the configuration phase is already completed and all user code related to it finished executing,
     * including late callbacks like {@link org.gradle.api.execution.TaskExecutionGraph#whenReady(Closure)}.
     * This callback is invoked before any of the tasks of the execution phase starts.
     * However, the tasks of the included builds that contribute project and settings plugins, and tasks of the buildSrc build
     * run before the execution phase, at the configuration phase, and, therefore, before this callback.
     *
     * Expected to be invoked at most once for a build tree, isn't invoked if the configuration phase fails.
     * The configuration cache doesn't affect this callback, it is invoked regardless of the cache entry being reused.
     *
     * @see org.gradle.internal.operations.BuildOperationCategory#RUN_MAIN_TASKS
     */
    default void executionPhaseStarted() {
    }

    /**
     * Notified when the build invocation has finished by Gradle.
     */
    GradleEnterprisePluginEndOfBuildListener getEndOfBuildListener();

}
