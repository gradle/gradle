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

package org.gradle.kotlin.dsl.execution

import org.gradle.api.internal.project.ProjectState
import org.gradle.execution.BatchScriptCompiler
import org.gradle.kotlin.dsl.provider.KotlinScriptClassloadingCache
import javax.inject.Inject


internal
class KotlinDslBatchScriptCompiler @Inject constructor(
    @Suppress("unused") private val scriptCache: KotlinScriptClassloadingCache
) : BatchScriptCompiler {

    override fun compile(parent: ProjectState, children: List<ProjectState>) {
        // acquire cache directory...
        // parse
        // partially evaluate
        // batch emit bytecode for all ResidualProgram at this point
        // scriptCache.put(...) all scripts
        TODO("Not yet implemented")
    }
}
