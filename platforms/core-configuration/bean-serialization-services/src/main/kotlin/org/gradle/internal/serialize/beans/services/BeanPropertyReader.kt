/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.beans.services

import org.gradle.api.internal.GeneratedSubclasses.unpack
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readPropertyValue
import org.gradle.internal.serialize.graph.reportUnsupportedFieldType
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.state.ModelObject
import java.lang.reflect.Field


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
    val originalType: Class<*> = unpack(beanType)

    private
    val constructorForSerialization by lazy(LazyThreadSafetyMode.PUBLICATION) {
        constructors.constructorForSerialization(beanType)
    }

    override fun ReadContext.newBean(): Any = if (originalType !== beanType) {
        instantiationScheme
            .withServices(ownerService<ServiceRegistry>())
            .deserializationInstantiator()
            .newInstance(originalType, Any::class.java)
    } else {
        constructorForSerialization.newInstance()
    }

    override suspend fun ReadContext.readStateOf(bean: Any) {
        readFieldsOf(bean)
        attachModelPropertiesOf(bean)
    }

    private
    suspend fun ReadContext.readFieldsOf(bean: Any) {
        for (relevantField in relevantFields) {
            readFieldOf(bean, relevantField)
        }
    }

    private
    fun attachModelPropertiesOf(bean: Any) {
        if (bean is ModelObject) {
            bean.attachModelProperties()
        }
    }

    private
    suspend fun ReadContext.readFieldOf(bean: Any, relevantField: RelevantField) {
        val field = relevantField.field
        val fieldName = field.name
        relevantField.unsupportedFieldType?.let {
            reportUnsupportedFieldType(it, "deserialize", fieldName)
        }
        readPropertyValue(PropertyKind.Field, fieldName) { fieldValue ->
            set(bean, field, fieldValue)
        }
    }

    private
    fun ReadContext.set(bean: Any, field: Field, value: Any?) {
        try {
            field.set(bean, value)
        } catch (_: Exception) {
            logNotAssignable(value, field)
        }
    }

    private
    fun ReadContext.logNotAssignable(value: Any?, field: Field) {
        logPropertyProblem("deserialize") {
            text("value ")
            reference(value.toString())
            text(" is not assignable to ")
            reference(field.type)
        }
    }
}
