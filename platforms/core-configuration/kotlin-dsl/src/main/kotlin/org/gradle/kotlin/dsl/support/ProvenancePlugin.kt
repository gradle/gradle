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

package org.gradle.kotlin.dsl.support

import org.gradle.api.internal.provider.DefaultProperty
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.FqName


internal
object ProvenanceCompilerPlugin {

    fun apply(project: Project) {
        IrGenerationExtension.registerExtension(project, ProvenanceGenerationExtension())
    }
}


private
class ProvenanceGenerationExtension : IrGenerationExtension {
    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.accept(object : IrElementTransformerVoidWithContext() {

            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.isPropertyAssignment()) {
                    val originalValue = expression.getValueArgument(0)
                    val provenanceOffset = originalValue!!.startOffset
                    val file = moduleFragment.files.first()
                    val fileEntry = file.fileEntry
                    val provenance = file.name + ":" + (fileEntry.getLineNumber(provenanceOffset) + 1) + ":" + fileEntry.getColumnNumber(provenanceOffset)

                    val targetType = pluginContext.referenceClass(FqName(DefaultProperty::class.qualifiedName!!))
                        ?: error("Class not found")

                    val withProv = IrSingleStatementBuilder(
                        pluginContext,
                        scope = scopeStack.peek()!!.scope,
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset
                    ).build {
                        irCall(
                            targetType.getSimpleFunction("withProv")!!
                        ).apply {
                            putValueArgument(0, irString(provenance))
                            putValueArgument(1, originalValue)
                        }
                    }

                    expression.putValueArgument(0, withProv)
                }
                return super.visitCall(expression)
            }
        }, null)
    }

    private
    fun IrCall.isPropertyAssignment() =
        valueArgumentsCount == 1 && symbol.owner.name.asString() == "assign"
}
