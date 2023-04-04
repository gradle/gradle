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

package org.gradle.kotlin.dsl.tooling.builders

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.tooling.ProjectConnection

trait KotlinScriptsModelFetcher {

    def kotlinDslScriptsModelFor(boolean lenient = false, File... scripts) {
        return kotlinDslScriptsModelFor(lenient, true, scripts.toList())
    }

    abstract <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl)


    def kotlinDslScriptsModelFor(boolean lenient = false, boolean explicitlyRequestPreparationTasks = true, Iterable<File> scripts) {
        String output = ""
        String error = ""
        def model = withConnection { connection ->
            def client = new KotlinDslScriptsModelClient()
            def m = client.fetchKotlinDslScriptsModel(
                connection,
                new KotlinDslScriptsModelRequest(
                    scripts.toList(),
                    null, null, [], [], lenient, explicitlyRequestPreparationTasks
                )
            )
            output = client.output.toString()
            error = client.error.toString()
            return m
        }
        return [model, OutputScrapingExecutionResult.from(output, error)]
    }
}
