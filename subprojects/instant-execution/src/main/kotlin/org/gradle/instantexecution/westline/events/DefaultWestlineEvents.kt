/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.westline.events

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskState
import org.gradle.api.westline.events.WestlineAfterTaskListener
import org.gradle.api.westline.events.WestlineBeforeTaskListener
import org.gradle.api.westline.events.WestlineEvents
import org.gradle.api.westline.events.WestlineListener
import org.gradle.api.westline.events.WestlineListenerParameters
import org.gradle.api.westline.events.WestlineListenerSpec
import org.gradle.api.westline.events.WestlineTaskExecutionResult
import org.gradle.api.westline.events.WestlineTaskInfo
import org.gradle.instantexecution.westline.extractParametersType
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.DefaultServiceRegistry


internal
class DefaultWestlineEvents(
    private val objects: ObjectFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val listenerManager: ListenerManager,
    private val isolatableFactory: IsolatableFactory
) : WestlineEvents {

    override fun <L : WestlineAfterTaskListener<P>, P : WestlineListenerParameters> afterTask(
        listenerType: Class<L>,
        configuration: Action<in WestlineListenerSpec<P>>
    ) {
        val withNewListener = prepareListenerOf(listenerType, configuration)
        listenerManager.addListener(object : TaskExecutionListenerAdapter() {
            override fun afterExecute(task: Task, state: TaskState) {
                withNewListener {
                    afterTask(
                        WestlineTaskInfo(task.path),
                        WestlineTaskExecutionResult(when {
                            state.skipped -> "SKIPPED"
                            state.upToDate -> "UP-TO-DATE"
                            state.executed -> "SUCCESS"
                            else -> TODO()
                        })
                    )
                }
            }
        })
    }

    override fun <L : WestlineBeforeTaskListener<P>, P : WestlineListenerParameters> beforeTask(
        listenerType: Class<L>,
        configuration: Action<in WestlineListenerSpec<P>>
    ) {
        val withNewListener = prepareListenerOf(listenerType, configuration)
        listenerManager.addListener(object : TaskExecutionListenerAdapter() {
            override fun beforeExecute(task: Task) {
                withNewListener {
                    beforeTask(WestlineTaskInfo(task.path))
                }
            }
        })
    }

    /**
     * Returns a function that can be used to execute an action against a new [listenerType] instance.
     */
    private
    fun <L : WestlineListener<P>, P : WestlineListenerParameters> prepareListenerOf(
        listenerType: Class<L>,
        configuration: Action<in WestlineListenerSpec<P>>
    ): (L.() -> Unit) -> Unit {
        val parametersType = extractParametersType<P, WestlineListener<P>, WestlineListenerParameters>(listenerType)
        val isolatedParameters = prepareParameters(parametersType, configuration)
        return { listenerAction ->
            listenerAction(
                createListener(listenerType, parametersType, isolatedParameters)
            )
        }
    }

    private
    fun <L : WestlineListener<P>, P : WestlineListenerParameters> createListener(
        listenerType: Class<L>,
        parametersType: Class<P>,
        isolatedParameters: Isolatable<P>
    ): L {
        val serviceRegistry = DefaultServiceRegistry().apply {
            add(parametersType, isolatedParameters.isolate())
        }
        return instantiatorFactory.inject(serviceRegistry).newInstance(listenerType)
    }

    private
    fun <P : WestlineListenerParameters> prepareParameters(
        parametersType: Class<P>,
        configuration: Action<in WestlineListenerSpec<P>>
    ): Isolatable<P> {
        val parameters = objects.newInstance(parametersType)
        configuration.execute(DefaultWestlineListenerSpec(parameters))
        return isolatableFactory.isolate(parameters)
    }
}


internal
class DefaultWestlineListenerSpec<P : WestlineListenerParameters>(
    private val parameters: P
) : WestlineListenerSpec<P> {
    override fun getParameters() = parameters
}


internal
open class TaskExecutionListenerAdapter : TaskExecutionListener {
    override fun beforeExecute(task: Task) = Unit
    override fun afterExecute(task: Task, state: TaskState) = Unit
}
