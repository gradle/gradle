/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.ntcscript


data class BuildScriptModel(
    val plugins: List<Plugin> = emptyList(),
    val extensions: List<Extension> = emptyList(),
    val dependencies: Map<String, List<Dependency>> = emptyMap()
) {
    data class ElementPosition(val line: Int, val column: Int)

    data class Plugin(val id: String, val position: ElementPosition?, val version: String?)

    data class Extension(val name: String, val properties: Map<String, PropertyValue>)

    sealed class Dependency {
        data class ExternalDependency(val group: String, val module: String, val version: String?) : Dependency()
        data class PlatformDependency(val group: String, val module: String, val version: String?) : Dependency()
        data class ProjectDependency(val id: String) : Dependency()
    }

    sealed class PropertyValue {
        data class IntValue(val value: Int) : PropertyValue()
        data class DoubleValue(val value: Double) : PropertyValue()
        data class StringValue(val value: String) : PropertyValue()
        data class RecordValue(val value: Map<String, PropertyValue>) : PropertyValue()
    }
}
