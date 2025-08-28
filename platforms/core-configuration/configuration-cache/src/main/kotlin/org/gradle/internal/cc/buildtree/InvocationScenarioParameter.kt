/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.buildtree

import org.gradle.internal.buildoption.InternalOption

enum class InvocationScenarioParameter(
    val runningTasks: Boolean,
    val buildingModels: Boolean,
    val value: String
) {

    ANY(true, true, "true"),
    TASKS(true, false, "tasks"),
    TOOLING(false, true, "tooling"),
    NONE(false, false, "false"),
    ;

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value }
    }

    class Option(private val systemProperty: String, private val default: InvocationScenarioParameter) : InternalOption<InvocationScenarioParameter> {
        override fun getDefaultValue(): InvocationScenarioParameter = default

        override fun getSystemPropertyName(): String = systemProperty

        override fun convert(value: String): InvocationScenarioParameter =
            fromValue(value) ?: error("Invalid value '$value' for system property '$systemProperty'. Allowed values are: ${entries.joinToString(",") { it.value }}")
    }
}
