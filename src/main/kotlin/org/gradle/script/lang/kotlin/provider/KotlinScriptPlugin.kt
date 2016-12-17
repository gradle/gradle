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

package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.loggerFor

import org.gradle.api.Project

import org.gradle.configuration.ScriptPlugin

import org.gradle.groovy.scripts.ScriptSource

import java.lang.IllegalArgumentException

class KotlinScriptPlugin(
    val scriptSource: ScriptSource,
    val script: (Project) -> Unit) : ScriptPlugin {

    private val logger = loggerFor<KotlinScriptPlugin>()

    override fun getSource() = scriptSource

    override fun apply(target: Any) {
        logger.debug("Applying Kotlin script to {}", target)
        if (target !is Project) {
            throw IllegalArgumentException("target $target was not a Project as expected")
        }
        script(target)
    }
}
