/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import java.io.File


interface DeclarativeSchemaRegistry {

    fun storeSchema(target: Any, identifier: String, schema: EvaluationSchema)

    fun projectAnalysisSchema(): AnalysisSchema

    fun evaluationSchemaForBuildFile(file: File): EvaluationSchema?
}


internal
class DefaultDeclarativeSchemaRegistry : DeclarativeSchemaRegistry {

    private
    val schemas: MutableMap<Any, EvaluationSchema> = mutableMapOf()

    override fun storeSchema(target: Any, identifier: String, schema: EvaluationSchema) {
        if (target is Project && identifier == "project" || target is Settings && identifier == "settings") {
            schemas[target] = schema
        }
    }

    override fun projectAnalysisSchema(): AnalysisSchema {
        schemas.forEach {
            val target = it.key
            if (target is Project) {
                val schema = it.value
                return schema.analysisSchema
            }
        }
        return AnalysisSchema.EMPTY
    }

    override fun evaluationSchemaForBuildFile(file: File): EvaluationSchema? {
        val searchPath = file.parentFile.absolutePath
        schemas.forEach {
            val target = it.key
            if (target is Project) {
                if (target.projectDir.absolutePath == searchPath) {
                    return it.value
                }
            } else if (target is Settings) {
                if (target.settingsDir.absolutePath == searchPath) {
                    return it.value
                }
            } else {
                error("Unhandled target type ${target.javaClass.simpleName}")
            }
        }
        return null
    }
}
