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

/**
 * A notification that a build operation has finished.
 *
 * The methods of this interface are awkwardly prefixed to allow
 * internal types to implement this interface along with other internal interfaces
 * without risking method collision.
 *
 * @since 4.4
 */
@UsedByScanPlugin
public interface BuildOperationProgressNotification {

    /**
     * The operation ID.
     *
     * Must be logically equal to a {@link BuildOperationStartedNotification#getNotificationOperationId()} value
     * of a previously emitted started notification.
     */
    Object getNotificationOperationId();

    /**
     * The time that the event occurred.
     *
     * @since 4.4
     */
    long getNotificationOperationProgressTimestamp();

    /**
     * A structured object providing details about the operation that was performed.
     */
    Object getNotificationOperationProgressDetails();

}
