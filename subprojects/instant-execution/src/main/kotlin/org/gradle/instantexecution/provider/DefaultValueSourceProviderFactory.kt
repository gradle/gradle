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

package org.gradle.instantexecution.provider

import com.google.common.reflect.TypeToken
import org.gradle.api.Action
import org.gradle.api.internal.provider.AbstractReadOnlyProvider
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.model.internal.type.ModelType
import java.lang.reflect.ParameterizedType


class DefaultValueSourceProviderFactory(
    private val objects: ObjectFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val isolatableFactory: IsolatableFactory
) : ValueSourceProviderFactory {

    override fun <T : Any, P : ValueSourceParameters> createProviderOf(
        valueSourceType: Class<out ValueSource<T, P>>,
        configuration: Action<in ValueSourceSpec<P>>
    ): Provider<T> {
        val parametersType = extractParametersType<P, ValueSource<T, P>, ValueSourceParameters>(
            valueSourceType,
            1
        )
        val parameters = objects.newInstance(parametersType)
        configuration.execute(DefaultValueSourceSpec(parameters))
        val isolatedParameters = isolatableFactory.isolate(parameters)
        return DefaultValueSourceProvider(
            valueSourceType,
            parametersType,
            isolatedParameters,
            instantiatorFactory
        )
    }
}


private
class DefaultValueSourceSpec<P : ValueSourceParameters>(
    private val parameters: P
) : ValueSourceSpec<P> {
    override fun getParameters(): P = parameters
    override fun parameters(configuration: Action<in P>) {
        configuration.execute(parameters)
    }
}


private
class DefaultValueSourceProvider<T, P : ValueSourceParameters>(
    private val valueSourceType: Class<out ValueSource<T, P>>,
    private val parametersType: Class<P>,
    private val parameters: Isolatable<P>,
    private val instantiatorFactory: InstantiatorFactory
) : AbstractReadOnlyProvider<T>() {

    private
    val value: T? by lazy {
        createValueSource().obtain()
    }

    private
    fun createValueSource(): ValueSource<T, P> = instantiatorFactory.newIsolatedInstance(
        valueSourceType,
        parametersType,
        parameters
    )

    override fun getType(): Class<T>? = null

    override fun isPresent(): Boolean = value != null

    override fun getOrNull(): T? = value
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


internal
fun <T, P> InstantiatorFactory.newIsolatedInstance(
    listenerType: Class<T>,
    parametersType: Class<P>,
    isolatedParameters: Isolatable<P>
): T = inject(DefaultServiceRegistry().apply {
    add(parametersType, isolatedParameters.isolate())
}).newInstance(listenerType)
