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

package org.gradle.instantexecution.westline

import com.google.common.reflect.TypeToken
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.internal.provider.AbstractReadOnlyProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.westline.WestlineService
import org.gradle.api.westline.WestlineServiceFactory
import org.gradle.api.westline.WestlineServiceParameters
import org.gradle.api.westline.WestlineServiceSpec
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.model.internal.type.ModelType
import java.lang.reflect.ParameterizedType


class DefaultWestlineServiceFactory(
    private val objects: ObjectFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val listenerManager: ListenerManager,
    private val isolatableFactory: IsolatableFactory
) : WestlineServiceFactory {

    override fun <T : WestlineService<P>, P : WestlineServiceParameters> createProviderOf(
        serviceType: Class<T>,
        configuration: Action<in WestlineServiceSpec<P>>
    ): Provider<T> {

        val parametersType = extractParametersType<P, WestlineService<P>, WestlineServiceParameters>(serviceType)
        val parameters = objects.newInstance(parametersType)
        configuration.execute(DefaultWestlineServiceSpec(parameters))
        val isolatedParameters = isolatableFactory.isolate(parameters)

        return WestlineServiceProvider(
            serviceType,
            parametersType,
            isolatedParameters,
            instantiatorFactory,
            listenerManager
        )
    }
}


class WestlineServiceProvider<T : WestlineService<P>, P : WestlineServiceParameters>(
    val serviceType: Class<T>,
    val parametersType: Class<P>,
    val parameters: Isolatable<P>,
    private val instantiatorFactory: InstantiatorFactory,
    private val listenerManager: ListenerManager
) : AbstractReadOnlyProvider<T>() {
    private
    var service: T? = null

    private
    fun createService(): T {
        val serviceRegistry = DefaultServiceRegistry().apply {
            add(parametersType, parameters.isolate())
        }
        val instantiator = instantiatorFactory.inject(serviceRegistry)
        return instantiator.newInstance(serviceType).also {
            registerForClosing(it)
        }
    }

    private
    fun registerForClosing(service: T) {
        if (service is AutoCloseable) {
            listenerManager.addListener(object : BuildAdapter() {
                override fun buildFinished(result: BuildResult) {
                    service.close()
                }
            })
        }
    }

    override fun getType() = serviceType

    override fun isPresent(): Boolean {
        return true
    }

    override fun getOrNull() = synchronized(this) {
        service ?: createService().also { service = it }
    }
}


private
class DefaultWestlineServiceSpec<P : WestlineServiceParameters>(
    private val params: P
) : WestlineServiceSpec<P> {

    override fun getParameters(): P = params

    override fun parameters(action: Action<in P>) {
        action.execute(params)
    }
}


/**
 * @param P the expected parameters type
 * @param S the service interface
 * @param PI the parameters interface
 */
internal
inline fun <P : PI, reified S : Any, reified PI : Any> extractParametersType(
    implementationClass: Class<*>,
    parameterIndex: Int = 0
): Class<P> {
    val superType = TypeToken.of(implementationClass).getSupertype(S::class.java.uncheckedCast()).type as ParameterizedType
    val parametersType: Class<P> = TypeToken.of(superType.actualTypeArguments[parameterIndex]).rawType.uncheckedCast()
    if (parametersType == PI::class.java) {
        val parametersInterfaceName = ModelType.of(PI::class.java).displayName
        throw IllegalArgumentException(
            "Could not create ${implementationClass.name} parameters: must use a sub-type of $parametersInterfaceName as parameter type. Use $parametersInterfaceName.None for executions without parameters."
        )
    }
    return parametersType
}
