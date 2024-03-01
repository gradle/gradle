/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.operations.execution;

import org.gradle.internal.scan.UsedByScanPlugin;

@UsedByScanPlugin("doesn't link against this type, but expects these values - See ExecuteTaskBuildOperationType")
public enum CachingDisabledReasonCategory {
    /**
     * Reason for disabled caching is not known.
     */
    UNKNOWN,

    /**
     * Caching has not been enabled for the build.
     */
    BUILD_CACHE_DISABLED,

    /**
     * Caching has not been enabled for the task.
     */
    NOT_ENABLED_FOR_TASK,

    /**
     * Caching has not been enabled for the work.
     *
     * @since 8.3
     */
    NOT_CACHEABLE,

    /**
     * The work has no outputs declared.
     */
    NO_OUTPUTS_DECLARED,

    /**
     * The work has a {@code org.gradle.api.file.FileTree} or {@code org.gradle.api.internal.file.collections.DirectoryFileTree} as an output.
     *
     * @since 5.0
     */
    NON_CACHEABLE_TREE_OUTPUT,

    /**
     * Condition enabling caching isn't satisfied.
     * <p>
     * E.g. for a task caching is disabled via {@code org.gradle.api.tasks.TaskOutputs#cacheIf(Spec)}.
     */
    CACHE_IF_SPEC_NOT_SATISFIED,

    /**
     * Condition disabling caching satisfied.
     * <p>
     * E.g. for a task, caching is disabled via {@code org.gradle.api.tasks.TaskOutputs#doNotCacheIf(String, Spec)}.
     */
    DO_NOT_CACHE_IF_SPEC_SATISFIED,

    /**
     * Work's outputs overlap with other work's.
     * <p>
     * As Gradle cannot safely determine which work each output file belongs to it disables caching.
     */
    OVERLAPPING_OUTPUTS,

    /**
     * The work has failed validation.
     */
    VALIDATION_FAILURE
}
