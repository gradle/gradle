/*
 * Copyright 2019 the original author or authors.
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

@file:JvmName("KotlinDslScriptsModelClient")

package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.tooling.models.KotlinDslScriptsModel
import org.gradle.tooling.ProjectConnection
import java.io.File


const val kotlinDslScriptsModelTargets = "org.gradle.kotlin.dsl.provider.scripts"


/**
 * Kotlin DSL model request for a set of scripts.
 */
data class KotlinDslScriptsModelRequest @JvmOverloads constructor(

    /**
     * The set of scripts for which a model is requested.
     * Must not be empty.
     */
    val scripts: List<File>,

    /**
     * Environment variables for the Gradle process.
     * Defaults will be used if `null` or empty.
     */
    val environmentVariables: Map<String, String>? = null,

    /**
     * Java home for the Gradle process.
     * Defaults will be used if `null`.
     */
    val javaHome: File? = null,

    /**
     * JVM options for the Gradle process.
     * Defaults to an empty list.
     */
    val jvmOptions: List<String> = emptyList(),

    /**
     * Gradle options.
     * Defaults to an empty list.
     */
    val options: List<String> = emptyList(),

    /**
     * Sets the leniency of the model builder.
     *
     * When set to `false` the model builder will fail on the first encountered problem.
     *
     * When set to `true` the model builder will make a best effort to collect problems,
     * answer a reasonable model with editor reports for each script.
     *
     * Defaults to `false`.
     *
     * @see org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel.editorReports
     */
    val lenient: Boolean = false,

    /**
     * Request correlation identifier.
     * For client/Gradle logs correlation.
     * Defaults to a time based identifier.
     */
    val correlationId: String = newCorrelationId()
)


/**
 * Fetches Kotlin DSL model for a set of scripts.
 *
 * @receiver the TAPI [ProjectConnection]
 * @param request the model request parameters
 * @return the model for all requested scripts
 */
fun ProjectConnection.fetchKotlinDslScriptsModel(request: KotlinDslScriptsModelRequest): KotlinDslScriptsModel =
    newKotlinDslScriptsModelBuilder(request.valid()).get()


private
fun KotlinDslScriptsModelRequest.valid(): KotlinDslScriptsModelRequest = apply {
    require(scripts.isNotEmpty()) { "At least one script must be requested" }
}


private
fun ProjectConnection.newKotlinDslScriptsModelBuilder(request: KotlinDslScriptsModelRequest) =
    model(KotlinDslScriptsModel::class.java).apply {

        if (request.environmentVariables?.isNotEmpty() == true) {
            setEnvironmentVariables(request.environmentVariables)
        }
        if (request.javaHome != null) {
            setJavaHome(request.javaHome)
        }

        if (request.lenient) setJvmArguments(request.jvmOptions + modelSpecificJvmOptions)
        else setJvmArguments(request.jvmOptions)

        forTasks(kotlinBuildScriptModelTask)

        val arguments = request.options.toMutableList()
        arguments += "-P$kotlinBuildScriptModelCorrelationId=${request.correlationId}"
        if (request.scripts.isNotEmpty()) {
            arguments += "-P$kotlinDslScriptsModelTargets=${request.scripts.joinToString("|") { it.canonicalPath }}"
        }
        withArguments(arguments)
    }
