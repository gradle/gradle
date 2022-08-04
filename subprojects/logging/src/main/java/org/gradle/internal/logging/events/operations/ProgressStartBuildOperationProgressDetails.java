/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.events.operations;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Build operation observer's view of {@link org.gradle.internal.logging.events.ProgressStartEvent}.
 *
 * See LoggingBuildOperationProgressBroadcaster.
 *
 * @since 4.7
 */
@UsedByScanPlugin("Non-internal replacement available since Gradle 7.4")
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public interface ProgressStartBuildOperationProgressDetails extends org.gradle.internal.operations.logging.ProgressStartBuildOperationProgressDetails {
    LogLevel getLogLevel();
}
