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

package org.gradle.internal.declarativedsl.software

import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import kotlin.reflect.KProperty
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

fun getSoftwareTypeModelInstance(softwareType: SoftwareTypeImplementation<*>, receiverObject: ProjectInternal): Any {
    fun Iterable<Annotation>.hasSoftwareTypeAnnotation() =
        any { annotation -> annotation is SoftwareType && annotation.name == softwareType.softwareType }

    val pluginInstance = receiverObject.plugins.getPlugin(softwareType.pluginClass)

    with(softwareType.pluginClass.kotlin) {
        (memberProperties + memberFunctions.filter { (it.parameters - it.instanceParameter).isEmpty() }).find { member ->
            member.annotations.hasSoftwareTypeAnnotation() || (member is KProperty<*> && member.getter.annotations.hasSoftwareTypeAnnotation())
        }?.let { accessor ->
            return checkNotNull(accessor.call(pluginInstance))
        }
    }

    // Fallback to Java accessors if Kotlin reflection metadata is lost or not available:
    softwareType.pluginClass.methods.find { it.annotations.toList().hasSoftwareTypeAnnotation() }
        ?.let { javaAccessor ->
            return javaAccessor.invoke(pluginInstance)
        }

    error("no property found for software type '$softwareType' in the plugin type '${softwareType.pluginClass.name}'")
}
