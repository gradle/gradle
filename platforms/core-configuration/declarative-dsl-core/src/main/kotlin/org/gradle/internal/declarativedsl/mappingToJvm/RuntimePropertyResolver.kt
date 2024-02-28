/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.Resolution.Resolved
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.Resolution.Unresolved
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties


interface RuntimePropertyResolver {
    fun resolvePropertyRead(receiverClass: KClass<*>, name: String): Resolution
    fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): Resolution

    sealed interface Resolution {
        data class Resolved(val property: RestrictedRuntimeProperty) : Resolution
        data object Unresolved : Resolution
    }
}


object ReflectionRuntimePropertyResolver : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution {
        val callable = receiverClass.memberProperties.find { it.name == name && it.visibility == KVisibility.PUBLIC }
            ?: receiverClass.memberFunctions.find { it.name == getterName(name) && it.parameters.size == 1 && it.visibility == KVisibility.PUBLIC }

        return when (callable) {
            null -> Unresolved
            else -> Resolved(object : RestrictedRuntimeProperty {
                override fun getValue(receiver: Any): Any? = callable.call(receiver)
                override fun setValue(receiver: Any, value: Any?) = throw UnsupportedOperationException()
            })
        }
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution {
        val setter = (receiverClass.memberProperties.find { it.name == name && it.visibility == KVisibility.PUBLIC } as? KMutableProperty<*>)?.setter
            ?: receiverClass.memberFunctions.find { it.name == setterName(name) && it.visibility == KVisibility.PUBLIC }

        return when (setter) {
            null -> Unresolved
            else -> Resolved(object : RestrictedRuntimeProperty {
                override fun getValue(receiver: Any): Any = throw UnsupportedOperationException()
                override fun setValue(receiver: Any, value: Any?) {
                    setter.call(receiver, value)
                }
            })
        }
    }

    private
    fun getterName(propertyName: String) = "get" + capitalize(propertyName)

    private
    fun setterName(propertyName: String) = "set" + capitalize(propertyName)

    private
    fun capitalize(propertyName: String) = propertyName.replaceFirstChar {
        if (it.isLowerCase()) it.uppercaseChar() else it
    }
}


class CompositePropertyResolver(private val resolvers: List<RuntimePropertyResolver>) : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution {
        resolvers.forEach {
            val resolution = it.resolvePropertyRead(receiverClass, name)
            if (resolution is Resolved)
                return resolution
        }
        return Unresolved
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution {
        resolvers.forEach {
            val resolution = it.resolvePropertyWrite(receiverClass, name)
            if (resolution is Resolved)
                return resolution
        }
        return Unresolved
    }
}
