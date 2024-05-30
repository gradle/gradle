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

package org.gradle.operations.execution;

import javax.annotation.Nullable;

/**
 * Fired when a deferred work execution finishes.
 *
 * @since 8.7
 */
public interface ExecuteDeferredWorkProgressDetails {

    /**
     * Type of work being executed.
     *
     * @since 8.7
     * @see ExecuteWorkBuildOperationType.Details#getWorkType()
     */
    @Nullable
    String getWorkType();

    /**
     * The opaque identity of the transform execution.
     * <p>
     * Unique within the current build tree.
     *
     * @since 8.7
     * @see ExecuteWorkBuildOperationType.Details#getIdentity()
     */
    String getIdentity();

    /**
     * The ID of the build that produced the outputs being reused.
     *
     * @since 8.7
     */
    String getOriginBuildInvocationId();

    /**
     * The build cache key of the work in the origin build invocation.
     *
     * @since 8.7
     */
    byte[] getOriginBuildCacheKeyBytes();

    /**
     * The execution time of the work in the build that produced the outputs being reused.
     *
     * @since 8.7
     */
    long getOriginExecutionTime();
}
