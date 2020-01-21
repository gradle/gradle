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

package org.gradle.instantexecution.serialization.beans

import org.gradle.api.GradleException
import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.logPropertyInfo
import org.gradle.instantexecution.serialization.logPropertyWarning
import org.gradle.instantexecution.serialization.ownerService
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.service.ServiceRegistry
import java.io.IOException
import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.function.Supplier


class BeanPropertyReader(
    private val beanType: Class<*>,
    private val constructors: BeanConstructors,
    instantiatorFactory: InstantiatorFactory
) : BeanStateReader {
    // TODO should use the same scheme as the original bean
    private
    val instantiationScheme: InstantiationScheme = instantiatorFactory.decorateScheme()

    private
    val fieldSetters = relevantStateOf(beanType).map { Pair(it.name, setterFor(it)) }

    private
    val constructorForSerialization by unsafeLazy {
        constructors.constructorForSerialization(beanType)
    }

    override suspend fun ReadContext.newBean(generated: Boolean) = if (generated) {
        val services = ownerService<ServiceRegistry>()
        instantiationScheme.withServices(services).deserializationInstantiator().newInstance(beanType, Any::class.java)
    } else {
        constructorForSerialization.newInstance()
    }

    override suspend fun ReadContext.readStateOf(bean: Any) {
        for (field in fieldSetters) {
            val fieldName = field.first
            val setter = field.second
            readPropertyValue(PropertyKind.Field, fieldName) { fieldValue ->
                setter(bean, fieldValue)
            }
        }
    }

    private
    fun setterFor(field: Field): ReadContext.(Any, Any?) -> Unit = when (val type = field.type) {
        Callable::class.java -> { bean, value ->
            field.set(bean, Callable { value })
        }
        Supplier::class.java -> { bean, value ->
            field.set(bean, Supplier { value })
        }
        Function0::class.java -> { bean, value ->
            field.set(bean, { value })
        }
        Lazy::class.java -> { bean, value ->
            field.set(bean, lazyOf(value))
        }
        else -> { bean, value ->
            if (isAssignableTo(type, value)) {
                field.set(bean, value)
            } else if (value != null) {
                logPropertyWarning("deserialize") {
                    text("value ")
                    reference(value.toString())
                    text(" is not assignable to ")
                    reference(type)
                }
            } // else null value -> ignore
        }
    }

    private
    fun isAssignableTo(type: Class<*>, value: Any?) =
        type.isInstance(value) ||
            type.isPrimitive && JavaReflectionUtil.getWrapperTypeForPrimitiveType(type).isInstance(value)
}


/**
 * Reads a sequence of properties written with [writingProperties].
 */
suspend fun ReadContext.readPropertyValue(kind: PropertyKind, name: String, action: (Any?) -> Unit) {
    withPropertyTrace(kind, name) {
        val value =
            try {
                read().also {
                    logPropertyInfo("deserialize", it)
                }
            } catch (passThrough: IOException) {
                throw passThrough
            } catch (passThrough: GradleException) {
                throw passThrough
            } catch (e: Exception) {
                throw GradleException("Could not load the value of $trace.", e)
            }
        action(value)
    }
}


internal
inline fun <T : IsolateContext, R> T.withPropertyTrace(kind: PropertyKind, name: String, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Property(kind, name, trace)) {
        action()
    }
