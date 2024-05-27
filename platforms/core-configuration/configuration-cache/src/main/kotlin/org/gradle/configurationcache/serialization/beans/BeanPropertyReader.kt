/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.beans

import org.gradle.api.GradleException
import org.gradle.internal.extensions.stdlib.unsafeLazy
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.state.ModelObject
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException


class BeanPropertyReader(
    private val beanType: Class<*>,
    private val constructors: BeanConstructors,
    instantiatorFactory: InstantiatorFactory
) : BeanStateReader {
    // TODO should use the same scheme as the original bean
    private
    val instantiationScheme: InstantiationScheme = instantiatorFactory.decorateScheme()

    private
    val relevantFields = relevantStateOf(beanType)

    private
    val constructorForSerialization by unsafeLazy {
        constructors.constructorForSerialization(beanType)
    }

    override fun ReadContext.newBean(generated: Boolean): Any = if (generated) {
        val services = ownerService<ServiceRegistry>()
        instantiationScheme.withServices(services).deserializationInstantiator().newInstance(beanType, Any::class.java)
    } else {
        constructorForSerialization.newInstance()
    }

    override suspend fun ReadContext.readStateOf(bean: Any) {
        for (relevantField in relevantFields) {
            val field = relevantField.field
            val fieldName = field.name
            relevantField.unsupportedFieldType?.let {
                reportUnsupportedFieldType(it, "deserialize", fieldName)
            }
            readPropertyValue(PropertyKind.Field, fieldName) { fieldValue ->
                set(bean, field, fieldValue)
            }
        }
        if (bean is ModelObject) {
            bean.attachModelProperties()
        }
    }

    private
    fun ReadContext.set(bean: Any, field: Field, value: Any?) {
        val type = field.type
        if (isAssignableTo(type, value)) {
            field.set(bean, value)
        } else if (value != null) {
            logPropertyProblem("deserialize") {
                text("value ")
                reference(value.toString())
                text(" is not assignable to ")
                reference(type)
            }
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
                read()
            } catch (passThrough: IOException) {
                throw passThrough
            } catch (passThrough: GradleException) {
                throw passThrough
            } catch (e: InvocationTargetException) {
                // unwrap ITEs as they are not useful to users
                throw GradleException("Could not load the value of $trace.", e.cause)
            } catch (e: Exception) {
                throw GradleException("Could not load the value of $trace.", e)
            }
        action(value)
    }
}


internal
inline fun <T : MutableIsolateContext, R> T.withPropertyTrace(kind: PropertyKind, name: String, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Property(kind, name, trace)) {
        action()
    }
