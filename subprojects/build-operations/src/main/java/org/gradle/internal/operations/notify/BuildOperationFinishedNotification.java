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
 * A notification that a build operation has finished.
 *
 * The methods of this interface are awkwardly prefixed to allow
 * internal types to implement this interface along with other internal interfaces
 * without risking method collision.
 *
 * @since 4.0
 */
@UsedByScanPlugin
public interface BuildOperationFinishedNotification {

    /**
     * The operation ID.
     *
     * Must be logically equal to a {@link BuildOperationStartedNotification#getNotificationOperationId()} value
     * of a previously emitted started notification.
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
     * The time that the operation finished.
     *
     * @since 4.2
     */
    long getNotificationOperationFinishedTimestamp();

    /**
     * A structured object providing details about the operation that was performed.
     */
    Object getNotificationOperationDetails();

    /**
     * A structured object representing the outcome of the operation.
     * Null if the operation failed, or if no result details are produced for the type of operation.
     */
    Object getNotificationOperationResult();

    /**
     * The operation failure.
     * Null if the operation was successful.
     */
    Throwable getNotificationOperationFailure();

}
