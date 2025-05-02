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

package org.gradle.kotlin.dsl.provider

import org.gradle.configuration.ScriptPlugin
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.kotlin.dsl.support.loggerFor


internal
class KotlinScriptPlugin(
    private val scriptSource: ScriptSource,
    private val script: (Any) -> Unit
) : ScriptPlugin {

    override fun getSource() =
        scriptSource

    override fun apply(target: Any) {
        logger.debug("Applying Kotlin script to {}", target)
        script(target)
    }
}


private
val logger = loggerFor<KotlinScriptPlugin>()
