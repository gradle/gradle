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

package org.gradle.declarative.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.declarative.dsl.tooling.models.DeclarativeFile
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.declarativedsl.analysis.defaultCodeResolver
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.DeclarativeSchemaRegistry
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder

class DeclarativeFileErrorsModelBuilder(
    private val textFileResourceLoader: TextFileResourceLoader,
    private val schemaRegistry: DeclarativeSchemaRegistry
) : ParameterizedToolingModelBuilder<DeclarativeFile> {
    override fun getParameterType(): Class<DeclarativeFile> =
        DeclarativeFile::class.java

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.declarative.dsl.tooling.models.DeclarativeFileErrorsModel"

    override fun buildAll(modelName: String, parameter: DeclarativeFile, project: Project): Any {
        val buildFile = parameter.file
        if (!buildFile.exists()) {
            // TODO: return empty result
        }

        val evaluationSchema = schemaRegistry.evaluationSchemaForBuildFile(buildFile)
        if (evaluationSchema == null) {
            // TODO: return empty result
            return ""
        }
        println("evaluationSchema = ${evaluationSchema}")

        val textResource = StringTextResource("build-file-content", parameter.content)
        val scriptSource: ScriptSource = TextResourceScriptSource(textResource)
        println("scriptSource = ${scriptSource}")

        val (tree, code, codeOffset) = parse(scriptSource.resource.text)
        val languageModel = DefaultLanguageTreeBuilder().build(tree, code, codeOffset, SourceIdentifier(scriptSource.fileName))
        // TODO: errors in language model

        val resolver = defaultCodeResolver(evaluationSchema.analysisStatementFilter)

        val resolution = resolver.resolve(evaluationSchema.analysisSchema, languageModel.imports, languageModel.topLevelBlock)
        val resolutionErrors = resolution.errors
        println("resolutionErrors = ${resolutionErrors}") // TODO: no resolution errors...

        TODO("Not yet implemented")
    }

    override fun buildAll(modelName: String, project: Project): Any {
        TODO("Not yet implemented: parameter NONE")
    }
}
