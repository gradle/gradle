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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import javax.annotation.Nullable

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.kotlin.dsl.resolver.KotlinBuildScriptModelRequestKt.newCorrelationId


class KotlinDslScriptsModelClient {

    /**
     * Fetches Kotlin DSL model for a set of scripts.
     *
     * @param connection the TAPI connection
     * @param request the model request parameters
     * @return the model for all requested scripts
     */
    KotlinDslScriptsModel fetchKotlinDslScriptsModel(ProjectConnection connection, KotlinDslScriptsModelRequest request) {
        return connection.model(KotlinDslScriptsModel).tap {

            if (request.environmentVariables != null && !request.environmentVariables.isEmpty()) {
                setEnvironmentVariables(request.environmentVariables)
            }
            if (request.javaHome != null) {
                setJavaHome(request.javaHome)
            }

            if (request.lenient) {
                setJvmArguments(request.jvmOptions + KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
            } else {
                setJvmArguments(request.jvmOptions + KotlinDslModelsParameters.STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
            }

            if (request.explicitlyRequestPreparationTasks) {
                forTasks(KotlinDslModelsParameters.PREPARATION_TASK_NAME)
            }

            def arguments = request.options +
                "-P${KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME}=${request.correlationId}".toString() +
                "-Dorg.gradle.internal.plugins.portal.url.override=${gradlePluginRepositoryMirrorUrl()}".toString()

            if (!request.scripts.isEmpty()) {
                arguments += "-P${KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME}=${request.scripts.collect { it.canonicalPath }.join("|")}".toString()
            }
            withArguments(arguments)

        }.get()
    }
}

/**
 * Kotlin DSL model request for a set of scripts.
 */
class KotlinDslScriptsModelRequest {

    /**
     * The set of scripts for which a model is requested.
     * If empty, the set of scripts known to participate in this build will be used.
     */
    final List<File> scripts

    /**
     * Environment variables for the Gradle process.
     * Defaults will be used if `null` or empty.
     */
    final Map<String, String> environmentVariables

    /**
     * Java home for the Gradle process.
     * Defaults will be used if `null`.
     */
    final File javaHome

    /**
     * JVM options for the Gradle process.
     * Defaults to an empty list.
     */
    final List<String> jvmOptions

    /**
     * Gradle options.
     * Defaults to an empty list.
     */
    final List<String> options

    /**
     * Sets the leniency of the model builder.
     *
     * When set to `false` the model builder will fail on the first encountered problem.
     *
     * When set to `true` the model builder will make a best effort to collect problems,
     * answer a reasonable model with editor reports for each script.
     *
     * Defaults to `false`.
     */
    final Boolean lenient

    /**
     * Request the kotlin-dsl preparation tasks to be run on the client side.
     */
    final Boolean explicitlyRequestPreparationTasks

    /**
     * Request correlation identifier.
     * For client/Gradle logs correlation.
     * Defaults to a time based identifier.
     */
    final String correlationId

    KotlinDslScriptsModelRequest(
        List<File> scripts,
        @Nullable Map<String, String> environmentVariables = null,
        @Nullable File javaHome = null,
        List<String> jvmOptions = [],
        List<String> options = [],
        Boolean lenient = false,
        Boolean explicitlyRequestPreparationTasks = true,
        String correlationId = newCorrelationId()
    ) {
        this.scripts = scripts
        this.environmentVariables = environmentVariables
        this.javaHome = javaHome
        this.jvmOptions = jvmOptions
        this.options = options
        this.lenient = lenient
        this.explicitlyRequestPreparationTasks = explicitlyRequestPreparationTasks
        this.correlationId = correlationId
    }
}
