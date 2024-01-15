/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.fixtures


import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.kotlin.dsl.resolver.KotlinBuildScriptModelRequestKt.newCorrelationId

class KotlinScriptModelParameters {
    static setModelParameters(modelBuilder, boolean lenient, boolean explicitlyRequestPreparationTasks = true, Iterable<File> scripts = []) {
        if (lenient) {
            modelBuilder.setJvmArguments([KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION])
        } else {
            modelBuilder.setJvmArguments([KotlinDslModelsParameters.STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION])
        }

        if (explicitlyRequestPreparationTasks) {
            modelBuilder.forTasks(KotlinDslModelsParameters.PREPARATION_TASK_NAME)
        }
        def correlationId = newCorrelationId()

        def arguments =
            ["-P${KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME}=${correlationId}".toString(),
             "-Dorg.gradle.internal.plugins.portal.url.override=${gradlePluginRepositoryMirrorUrl()}".toString()]

        if (!scripts.toList().isEmpty()) {
            arguments += "-P${KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME}=${scripts.toList().collect { it.canonicalPath }.join("|")}".toString()
        }
        modelBuilder.withArguments(arguments)
    }
}
