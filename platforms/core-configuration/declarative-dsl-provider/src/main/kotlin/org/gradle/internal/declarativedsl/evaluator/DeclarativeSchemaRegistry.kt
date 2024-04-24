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
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema


interface DeclarativeSchemaRegistry {

    fun storeSchema(target: Any, identifier: String, schema: AnalysisSchema)

    fun projectSchema(): AnalysisSchema
}


internal
class DefaultDeclarativeSchemaRegistry : DeclarativeSchemaRegistry {

    private
    val schemas: MutableMap<Pair<Any, String>, AnalysisSchema> = mutableMapOf()

    override fun storeSchema(target: Any, identifier: String, schema: AnalysisSchema) {
        schemas[target to identifier] = schema
    }

    override fun projectSchema(): AnalysisSchema {
        schemas.forEach {
            val target = it.key.first
            val identifier = it.key.second
            if (target is Project && identifier == "project") {
                val schema = it.value
                return schema
            }
        }
        return DefaultAnalysisSchema.Empty
    }
}
