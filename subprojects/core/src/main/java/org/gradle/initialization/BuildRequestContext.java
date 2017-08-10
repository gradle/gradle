/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization;

/**
 * Provides access to services provided by build requester.
 */
public interface BuildRequestContext extends BuildRequestMetaData {
    /**
     * Returns the cancellation token through which the requester can cancel the build.
     */
    BuildCancellationToken getCancellationToken();

    /**
     * Returns an event consumer that will forward events to the build requester.
     */
    BuildEventConsumer getEventConsumer();

    ContinuousExecutionGate getExecutionGate();
}
