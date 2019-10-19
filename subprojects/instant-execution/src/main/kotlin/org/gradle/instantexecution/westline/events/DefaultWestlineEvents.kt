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

import com.google.common.reflect.TypeToken
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
import org.gradle.internal.Cast
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.workers.WorkParameters
import java.lang.reflect.ParameterizedType


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
        val parametersType = extractParametersType(listenerType)
        val isolatedParameters = prepareParameters<P>(parametersType, configuration)

        listenerManager.addListener(object : TaskExecutionListener {

            override fun beforeExecute(task: Task) {}

            override fun afterExecute(task: Task, state: TaskState) {
                val listener = createListener(listenerType, parametersType, isolatedParameters)
                listener.afterTask(
                    WestlineTaskInfo(task.path),
                    WestlineTaskExecutionResult(when {
                        state.skipped -> "SKIPPED"
                        state.upToDate -> "UP-TO-DATE"
                        state.executed -> "SUCCESS"
                        else -> TODO()
                    })
                )
            }
        })
    }

    override fun <L : WestlineBeforeTaskListener<P>, P : WestlineListenerParameters> beforeTask(
        listenerType: Class<L>,
        configuration: Action<in WestlineListenerSpec<P>>
    ) {
        val parametersType = extractParametersType(listenerType)
        val isolatedParameters = prepareParameters<P>(parametersType, configuration)

        listenerManager.addListener(object : TaskExecutionListener {

            override fun beforeExecute(task: Task) {
                val listener = createListener(listenerType, parametersType, isolatedParameters)
                listener.beforeTask(WestlineTaskInfo(task.path))
            }

            override fun afterExecute(task: Task, state: TaskState) {}
        })
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
        val instantiator = instantiatorFactory.inject(serviceRegistry)
        val listener = instantiator.newInstance(listenerType)
        return listener
    }

    private
    fun <P : WestlineListenerParameters> prepareParameters(
        parametersType: Class<P>,
        configuration: Action<in WestlineListenerSpec<P>>
    ): Isolatable<P> {
        val parameters = objects.newInstance(parametersType)
        configuration.execute(DefaultWestlineListenerSpec(parameters))
        val isolatedParameters = isolatableFactory.isolate(parameters)
        return isolatedParameters
    }


    private
    fun <T : WestlineListener<P>, P : WestlineListenerParameters> extractParametersType(
        implementationClass: Class<T>
    ): Class<P> {
        val superType = TypeToken.of(implementationClass).getSupertype(WestlineListener::class.java).type as ParameterizedType
        val parameterType: Class<P> = Cast.uncheckedNonnullCast(TypeToken.of(superType.actualTypeArguments[0]).rawType)
        if (parameterType == WestlineListenerParameters::class.java) {
            throw IllegalArgumentException(String.format("Could not create listener parameters: must use a sub-type of %s as parameter type. Use %s for executions without parameters.", ModelType.of(WestlineListenerParameters::class.java).displayName, ModelType.of(WorkParameters.None::class.java).displayName))
        }
        return parameterType
    }
}


class DefaultWestlineListenerSpec<P : WestlineListenerParameters>(
    private val parameters: P
) : WestlineListenerSpec<P> {
    override fun getParameters() = parameters
}
