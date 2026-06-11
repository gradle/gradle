/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.GradleException
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.ResolutionProvider


/**
 * Thrown during configuration-cache load when the loaded work graph contains two tasks
 * that declare the same CLI option name with conflicting `executionTimeOnly` semantics:
 * one task ([executionTimeOnlyTaskPath]) declares it `@Option(executionTimeOnly = true)`,
 * while another task ([configTimeTaskPath]) declares the same option with the default
 * configuration-time semantics. The two are not safe to invoke in the same build.
 *
 * If the contributing task can't be identified in the current work graph (e.g. the
 * manifest is stale after a build-script change removed the execution-time-only task),
 * [executionTimeOnlyTaskPath] will be `null` and the message + resolutions degrade to
 * describe the violator alone. Callers should use
 * [ExecutionTimeOnlyOptionsManifestService.findExecutionTimeOnlyContributor] to look up
 * the contributor before constructing this exception.
 *
 * Propagates through [DefaultConfigurationCache] as a [GradleException]; Gradle's
 * error renderer surfaces the [ResolutionProvider] suggestions in the "Possible
 * solutions" section.
 */
@Contextual
internal
class ExecutionTimeOnlyOptionsValidationException(
    val executionTimeOnlyTaskPath: String?,
    val configTimeTaskPath: String,
    val optionName: String
) : GradleException(buildMessage(executionTimeOnlyTaskPath, configTimeTaskPath, optionName)),
    ResolutionProvider {

    override fun getResolutions(): List<String> =
        if (executionTimeOnlyTaskPath != null) {
            listOf(
                "Annotate '--$optionName' on '$configTimeTaskPath' with @Option(executionTimeOnly = true) if its value is in fact applied at execution time and not consulted during configuration.",
                "Use a different option name on one of the two tasks to avoid the collision.",
                "Invoke the two tasks in separate builds."
            )
        } else {
            listOf(
                "Annotate '--$optionName' on '$configTimeTaskPath' with @Option(executionTimeOnly = true) if its value is in fact applied at execution time.",
                "Delete the .gradle/configuration-cache directory to clear the stale manifest entry."
            )
        }

    private companion object {
        fun buildMessage(
            executionTimeOnlyTaskPath: String?,
            configTimeTaskPath: String,
            optionName: String
        ): String =
            if (executionTimeOnlyTaskPath != null) {
                "Configuration cache entry cannot be reused: task '$executionTimeOnlyTaskPath' declares " +
                    "option '--$optionName' as execution-time-only, but another task '$configTimeTaskPath' " +
                    "uses an option with the same name that is allowed to be read during Configuration time. " +
                    " These tasks cannot be invoked together."
            } else {
                "Configuration cache entry cannot be reused: task '$configTimeTaskPath' declares option " +
                    "'--$optionName' as not execution-time-only, but the configuration cache entry was hashed " +
                    "assuming it is. The manifest may be stale (no current task declares this option as " +
                    "execution-time-only)."
            }
    }
}
