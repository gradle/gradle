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

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.operations.recorder.BuildOperationRecorder;
import org.gradle.internal.progress.BuildOperationListenerManager;

/**
 * Services enabling build operation notifications.
 *
 * Build tree scoped.
 */
public class BuildOperationNotificationServices {

    BuildOperationNotificationListenerRegistrar createBuildOperationNotificationListenerRegistrar(
        BuildOperationListenerManager buildOperationListenerManager,
        BuildOperationRecorder buildOperationRecorder,
        GradleInternal gradleInternal
    ) {
        // The listener manager must be build session scoped, not global.
        return new BuildOperationNotificationBridge(buildOperationListenerManager, buildOperationRecorder, gradleInternal);
    }

}
