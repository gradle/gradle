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

package org.gradle.configurationcache.build

import org.gradle.api.internal.GradleInternal
import org.gradle.internal.build.BuildModelController


// The model for this build is already fully populated and the tasks scheduled when it is created, so this controller does not need to do anything
class NoOpBuildModelController(val gradle: GradleInternal) : BuildModelController {
    // TODO - this method should fail, as the fully configured settings object is not actually available
    override fun getLoadedSettings() = gradle.settings

    // TODO - this method should fail, as the fully configured build model is not actually available
    override fun getConfiguredModel() = gradle

    override fun prepareToScheduleTasks() {
        // Already done
    }

    override fun scheduleRequestedTasks() {
        // Already done
    }
}
