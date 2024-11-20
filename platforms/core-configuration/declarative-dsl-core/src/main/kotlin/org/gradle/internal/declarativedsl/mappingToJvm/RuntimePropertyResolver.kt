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

import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.ReadResolution
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.ReadResolution.ResolvedRead
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.ReadResolution.UnresolvedRead
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.WriteResolution
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.WriteResolution.ResolvedWrite
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver.WriteResolution.UnresolvedWrite
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure


interface RuntimePropertyResolver {
    fun resolvePropertyRead(receiverClass: KClass<*>, name: String): ReadResolution
    fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): WriteResolution

    sealed interface ReadResolution {
        data class ResolvedRead(val getter: DeclarativeRuntimePropertyGetter) : ReadResolution
        data object UnresolvedRead : ReadResolution
    }

    sealed interface WriteResolution {
        data class ResolvedWrite(val setter: DeclarativeRuntimePropertySetter) : WriteResolution
        data object UnresolvedWrite : WriteResolution
    }
}


object ReflectionRuntimePropertyResolver : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): ReadResolution {
        val getter = findKotlinProperty(receiverClass, name)?.let(::kotlinPropertyGetter)
            ?: findKotlinFunctionGetter(receiverClass, name)
            ?: findJavaGetter(receiverClass, name)

        return getter?.let(::ResolvedRead) ?: UnresolvedRead
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): WriteResolution {
        val setter = (findKotlinProperty(receiverClass, name) as? KMutableProperty<*>)?.let(::kotlinPropertySetter)
            ?: findKotlinFunctionSetter(receiverClass, name)
            ?: findJavaSetter(receiverClass, name)

        return setter?.let(::ResolvedWrite) ?: UnresolvedWrite
    }

    private
    fun findKotlinProperty(receiverClass: KClass<*>, name: String) =
        receiverClass.memberProperties.find { it.name == name && it.visibility == KVisibility.PUBLIC }

    private
    fun kotlinPropertyGetter(property: KProperty<*>) =
        DeclarativeRuntimePropertyGetter {
            property.call(it) to property.returnType.jvmErasure
        }

    private
    fun kotlinPropertySetter(property: KMutableProperty<*>) =
        DeclarativeRuntimePropertySetter { receiver, value -> property.setter.call(receiver, value) }

    private
    fun findKotlinFunctionGetter(receiverClass: KClass<*>, name: String) =
        receiverClass.memberFunctions.find { function -> function.name == getterName(name) && function.parameters.size == 1 && function.visibility == KVisibility.PUBLIC }
            ?.let { function -> DeclarativeRuntimePropertyGetter {
                function.call(it) to function.returnType.jvmErasure
            } }

    private
    fun findKotlinFunctionSetter(receiverClass: KClass<*>, name: String) =
        receiverClass.memberFunctions.find { it.name == setterName(name) && it.visibility == KVisibility.PUBLIC }
            ?.let { function -> DeclarativeRuntimePropertySetter { receiver: Any, value: Any? -> function.call(receiver, value) } }

    private
    fun findJavaGetter(receiverClass: KClass<*>, name: String) =
        receiverClass.java.methods.find { it.name == getterName(name) && it.parameters.isEmpty() && it.modifiers.and(Modifier.PUBLIC) != 0 }
            ?.let { method -> DeclarativeRuntimePropertyGetter {
                method.invoke(it) to method.returnType.kotlin
            } }

    private
    fun findJavaSetter(receiverClass: KClass<*>, name: String) =
        receiverClass.java.methods.find { it.name == setterName(name) && it.parameters.size == 1 && it.modifiers.and(Modifier.PUBLIC) != 0 }
            ?.let { method -> DeclarativeRuntimePropertySetter { receiver: Any, value: Any? -> method.invoke(receiver, value) } }

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
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): ReadResolution {
        resolvers.forEach {
            val resolution = it.resolvePropertyRead(receiverClass, name)
            if (resolution !is UnresolvedRead)
                return resolution
        }
        return UnresolvedRead
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): WriteResolution {
        resolvers.forEach {
            val resolution = it.resolvePropertyWrite(receiverClass, name)
            if (resolution !is UnresolvedWrite)
                return resolution
        }
        return UnresolvedWrite
    }
}
