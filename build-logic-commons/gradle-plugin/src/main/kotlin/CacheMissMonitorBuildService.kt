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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

abstract class CacheMissMonitorBuildService @Inject constructor(objects: ObjectFactory) : BuildService<CacheMissMonitorBuildService.Params>, OperationCompletionListener {
    val cacheMiss: AtomicBoolean = AtomicBoolean(false)

    interface Params : BuildServiceParameters {
        val monitoredTaskPaths: SetProperty<String>
    }

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }

        val taskPath = event.descriptor.taskPath
        if (!parameters.monitoredTaskPaths.get().contains(taskPath)) {
            return
        }
        val taskResult = event.result
        if (taskResult is TaskSuccessResult && !taskResult.isFromCache && !taskResult.isUpToDate) {
            println("CACHE_MISS in task $taskPath")
            cacheMiss.set(true)
        }
    }
}
