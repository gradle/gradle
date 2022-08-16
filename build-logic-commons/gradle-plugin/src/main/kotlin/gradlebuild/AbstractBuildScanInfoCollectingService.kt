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

package gradlebuild

import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationResult
import java.io.Serializable

/**
 * A BuildService which monitors a few tasks (in both build-logic and main build) and collects information for build scan.
 * It's currently implemented by two use cases:
 * 1. Collect cache misses for compilation tasks and publish a `CACHE_MISS` tag for build scan.
 * 2. Collect failed task paths and display a link which points to the corresponding task in the build scan, like `https://ge.gradle.org/s/xxx/console-log?task=yyy`.
 */
abstract class AbstractBuildScanInfoCollectingService : BuildService<AbstractBuildScanInfoCollectingService.Params>, OperationCompletionListener {
    /**
     * To be compatible with configuration cache, this field must be Serializable.
     */
    abstract val collectedInformation: Serializable

    interface Params : BuildServiceParameters {
        val monitoredTaskPaths: SetProperty<String>
    }

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }

        val taskPath = event.descriptor.taskPath
        if (shouldInclude(taskPath)) {
            action(taskPath, event.result)
        }
    }

    protected open fun shouldInclude(taskPath: String): Boolean = parameters.monitoredTaskPaths.get().contains(taskPath)

    abstract fun action(taskPath: String, taskResult: TaskOperationResult)
}
