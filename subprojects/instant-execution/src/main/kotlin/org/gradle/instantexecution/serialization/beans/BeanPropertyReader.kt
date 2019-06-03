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
import org.gradle.api.internal.provider.DefaultPropertyState
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.logProperty
import org.gradle.instantexecution.serialization.logPropertyWarning
import org.gradle.internal.reflect.JavaReflectionUtil
import sun.reflect.ReflectionFactory
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
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

    fun ReadContext.readFieldsOf(bean: Any) {
        while (true) {
            val (fieldName, fieldValue) = readNextProperty(PropertyKind.Field) ?: break
            val setter = setterByFieldName.getValue(fieldName)
            setter(bean, fieldValue)
        }
    }

    /**
     * Returns `null` when there are no more properties to be read.
     */
    fun ReadContext.readNextProperty(kind: PropertyKind): Pair<String, Any?>? {

        val name = readString()
        if (name.isEmpty()) {
            return null
        }

        val value =
            try {
                read().also {
                    logProperty("deserialize", kind, beanType, name, it)
                }
            } catch (e: Throwable) {
                throw GradleException("Could not load the value of $kind '${beanType.name}.$name'.", e)
            }

        return name to value
    }

    @Suppress("unchecked_cast")
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
            Property::class.java -> { bean, value ->
                field.set(bean, DefaultPropertyState(Any::class.java).apply {
                    set(value)
                })
            }
            Provider::class.java -> { bean, value ->
                field.set(bean, when (value) {
                    null -> Providers.notDefined()
                    else -> Providers.of(value)
                })
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
                    logPropertyWarning("deserialize", PropertyKind.Field, beanType, field.name, "value $value is not assignable to $type")
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
