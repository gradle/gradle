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
 * A listener to notifications about build events.
 *
 * Implementations are thread safe and can be signalled concurrently.
 * However, a finished signal must not be emitted before the signal of the
 * corresponding started event has returned.
 *
 * Implementations may retain the notification values beyond the method that passed them.
 * Started notifications maybe held until a corresponding finished notification, and slightly after.
 * Finished notifications maybe held for a short time after the return of the finished signal for
 * off thread processing.
 *
 * Implementations must not error from either signal.
 * Callers should ignore any exceptions thrown by these methods.
 *
 * @since 4.0
 */
@UsedByScanPlugin("implemented by the scan plugin - 1.8 - 1.10")
public interface BuildOperationNotificationListener {

    void started(BuildOperationStartedNotification notification);

    void finished(BuildOperationFinishedNotification notification);

}
