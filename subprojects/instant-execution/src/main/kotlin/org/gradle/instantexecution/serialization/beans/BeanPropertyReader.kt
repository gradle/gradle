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

import groovy.lang.GroovyObjectSupport
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.logPropertyInfo
import org.gradle.instantexecution.serialization.logPropertyWarning
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.internal.reflect.JavaReflectionUtil
import sun.reflect.ReflectionFactory
import java.io.File
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.function.Supplier


class BeanPropertyReader(
    private val beanType: Class<*>,
    private val filePropertyFactory: FilePropertyFactory
) {

    companion object {

        fun factoryFor(
            filePropertyFactory: FilePropertyFactory
        ): (Class<*>) -> BeanPropertyReader = { type ->
            BeanPropertyReader(type, filePropertyFactory)
        }
    }

    private
    val setterByFieldName = relevantStateOf(beanType).associateBy(
        { it.name },
        { setterFor(it) }
    )

    private
    val constructorForSerialization by lazy {
        if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
            // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
            newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
        } else {
            newConstructorForSerialization(beanType, Object::class.java.getConstructor())
        }
    }

    fun newBean(): Any =
        constructorForSerialization.newInstance()

    suspend fun ReadContext.readFieldsOf(bean: Any) {
        readEachProperty(PropertyKind.Field) { fieldName, fieldValue ->
            val setter = setterByFieldName.getValue(fieldName)
            setter(bean, fieldValue)
        }
    }

    private
    fun setterFor(field: Field): ReadContext.(Any, Any?) -> Unit =

        when (val type = field.type) {
            DirectoryProperty::class.java -> { bean, value ->
                field.set(bean, filePropertyFactory.newDirectoryProperty().apply {
                    set(value as File?)
                })
            }
            RegularFileProperty::class.java -> { bean, value ->
                field.set(bean, filePropertyFactory.newFileProperty().apply {
                    set(value as File?)
                })
            }
            ListProperty::class.java -> { bean, value ->
                field.set(bean, DefaultListProperty(Any::class.java).apply {
                    set(value as List<Any?>)
                })
            }
            Property::class.java -> { bean, value ->
                field.set(bean, DefaultProperty(Any::class.java).apply {
                    set(value)
                })
            }
            Provider::class.java -> { bean, value ->
                field.set(bean, when (value) {
                    null -> Providers.notDefined()
                    else -> Providers.of(value)
                })
            }
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

    // TODO: What about the runtime decorations a serialized bean might have had at configuration time?
    private
    fun newConstructorForSerialization(beanType: Class<*>, constructor: Constructor<*>): Constructor<out Any> =
        ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, constructor)
}


/**
 * Reads a sequence of properties written with [writingProperties].
 */
suspend fun ReadContext.readEachProperty(kind: PropertyKind, action: (String, Any?) -> Unit) {
    while (true) {

        val name = readString()
        if (name.isEmpty()) {
            break
        }

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
            action(name, value)
        }
    }
}


internal
inline fun <T : IsolateContext, R> T.withPropertyTrace(kind: PropertyKind, name: String, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Property(kind, name, trace)) {
        action()
    }
