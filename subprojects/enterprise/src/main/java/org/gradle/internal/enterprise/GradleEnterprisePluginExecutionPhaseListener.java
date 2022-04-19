/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise;

/**
 * Used to signal a start of the execution phase to the plugin.
 *
 * Expected to be invoked at most once for a build tree, isn't invoked if the configuration phase fails.
 * The configuration cache doesn't affect this callback, it is invoked regardless of the cache entry being reused.
 *
 * Implemented by the Enterprise plugin.
 *
 * @see org.gradle.internal.operations.BuildOperationCategory#RUN_MAIN_TASKS
 */
public interface GradleEnterprisePluginExecutionPhaseListener {
    void executionPhaseStarted();
}
