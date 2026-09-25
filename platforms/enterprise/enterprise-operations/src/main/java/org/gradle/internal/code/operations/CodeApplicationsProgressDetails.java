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

package org.gradle.internal.code.operations;

import java.util.Map;

/**
 * Details about the code applications within a build.
 * <p>
 * Emitted once per build, after all user code has run.
 *
 * @since 9.9.0
 */
public interface CodeApplicationsProgressDetails {

    /**
     * The code applications of the build, keyed by application ID.
     * <p>
     * Applications that did not measurably execute any user code may be omitted.
     *
     * @see org.gradle.api.internal.plugins.ApplyPluginBuildOperationType.Details#getApplicationId()
     * @see org.gradle.configuration.ApplyScriptPluginBuildOperationType.Details#getApplicationId()
     *
     * @since 9.9.0
     */
    Map<Long, CodeApplication> getCodeApplications();

    /**
     * A single application of user code against some target, such as the application of a plugin
     * or a script against a project.
     *
     * @since 9.9.0
     */
    interface CodeApplication {

        /**
         * A map from code types to the time in nanoseconds this application spent executing that type of code.
         * <p>
         * Code types the application spent no measurable time in may be omitted.
         *
         * @since 9.9.0
         */
        Map<CodeType, Long> getTimings();

    }

    /**
     * A category of code that an application spent time executing.
     *
     * @see org.gradle.internal.code.UserCodeApplicationContext.CodeType
     *
     * @since 9.9.0
     */
    enum CodeType {

        /**
         * Code executed synchronously while the application is being applied.
         *
         * @since 9.9.0
         */
        MAIN,

        /**
         * Callbacks executed against a domain object collection.
         *
         * @since 9.9.0
         */
        COLLECTION_CALLBACK,

        /**
         * Asynchronous listener callbacks.
         *
         * @since 9.9.0
         */
        LISTENER,

        /**
         * Code executed as part of a task action.
         *
         * @since 9.9.0
         */
        TASK_ACTION,

        /**
         * Code executed to build a tooling model.
         *
         * @since 9.9.0
         */
        TOOLING_MODEL_BUILDER,

    }

}
