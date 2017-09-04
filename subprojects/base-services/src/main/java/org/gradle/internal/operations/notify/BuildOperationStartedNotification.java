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

package org.gradle.internal.operations.notify;

import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

/**
 * A notification that a build operation has started.
 *
 * The methods of this interface are awkwardly prefixed to allow
 * internal types to implement this interface along with other internal interfaces
 * without risking method collision.
 *
 * @since 4.0
 */
@UsedByScanPlugin
public interface BuildOperationStartedNotification {

    /**
     * A unique, opaque, value identifying this operation.
     */
    Object getNotificationOperationId();

    /**
     * The ID of the parent of this notification.
     *
     * Note: this is the ID of the nearest parent operation that also resulted in a notification.
     * As notifications are not sent for all operations, this may be a different value to the
     * parent operation ID.
     *
     * Null if the operation has no parent.
     */
    @Nullable
    Object getNotificationOperationParentId();

    /**
     * The time that the operation started.
     *
     * @since 4.2
     */
    long getNotificationOperationStartedTimestamp();

    /**
     * A structured object providing details about the operation to be performed.
     */
    Object getNotificationOperationDetails();

    /*
        Timestamps are conspicuously absent here.

        Timestamps are missing because the build scan plugin has different timestamp requirements.
        Specifically, it goes to some effort to provide monotonic timestamps (and the observed value) if different,
        and deterministic ordering of events that yield the same timestamp value (e.g. two clock reads quicker than the clock granularity).
     */

}
