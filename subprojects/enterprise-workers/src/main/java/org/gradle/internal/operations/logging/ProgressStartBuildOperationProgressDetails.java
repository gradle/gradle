/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.operations.logging;

/**
 * Build operation observer's view of {@code org.gradle.internal.logging.events.ProgressStartEvent}.
 *
 * See LoggingBuildOperationProgressBroadcaster.
 *
 * @since 7.4
 */
public interface ProgressStartBuildOperationProgressDetails {

    String getDescription();

    String getCategory();

    LogEventLevel getLevel();

    /**
     * While this may be null on the underlying implementation,
     * objects with a null value for this will not be forwarded as build operation progress.
     * Therefore, when observing as build operation progress this is never null.
     */
    String getLoggingHeader();

}
