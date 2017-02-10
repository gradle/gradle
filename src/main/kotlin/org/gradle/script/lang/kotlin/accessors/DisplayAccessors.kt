/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.accessors

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


open class DisplayAccessors : DefaultTask() {

    override fun getGroup() =
        "help"

    override fun getDescription() =
        "Displays the Kotlin code for accessing the available project extensions and conventions."

    @Suppress("unused")
    @TaskAction
    fun printExtensions() {
        schemaFor(project).withKotlinTypeStrings().forEachAccessor {
            println()
            println(it)
            println()
        }
    }
}



