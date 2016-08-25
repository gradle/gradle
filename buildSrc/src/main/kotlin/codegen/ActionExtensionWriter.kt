/*
 * Copyright 2016 the original author or authors.
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

package codegen

import org.gradle.script.lang.kotlin.codegen.ClassSignature
import org.gradle.script.lang.kotlin.codegen.GenericType
import org.gradle.script.lang.kotlin.codegen.JvmType
import org.gradle.script.lang.kotlin.codegen.MethodDescriptor
import org.gradle.script.lang.kotlin.codegen.isActionClassType
import org.gradle.script.lang.kotlin.codegen.conflictsWithExtension

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

import java.io.Writer

class ActionExtensionWriter(val writer: Writer, val kDocProvider: KDocProvider? = null) {

    init {
        writeHeader()
    }

    fun writeExtensionsFor(classNode: ClassNode) {
        if (isGeneric(classNode)) {
            return
        }
        val className = classNode.name.replace('/', '.')
        for (method in methodsRequiringExtensionIn(classNode)) {
            try {
                writeExtensionFor(className, method)
            } catch(e: Exception) {
                failedToWrite(className, method, e)
            }
        }
    }

    private fun writeHeader() {
        write("""$licenseHeader

package org.gradle.script.lang.kotlin

import org.gradle.api.Action

""")
    }

    private fun writeExtensionFor(className: String, method: MethodDescriptor) {

        data class Parameter(val name: String, val type: String)

        val signature = method.signature
        val parameterTypeNames = signature.parameters.map { kotlinTypeNameFor(it) }

        val kDoc = kDocFor(className, method, parameterTypeNames)

        val parameterNames = kDoc?.parameterNames ?: (0..parameterTypeNames.size).map { "p$it" }
        val parameters = parameterNames.zip(parameterTypeNames, ::Parameter)

        // kdoc if available
        kDoc?.let { write(it.format()) }

        // extension declaration
        write("fun ")
        if (signature.typeParameters.isNotEmpty()) {
            write(signature.typeParameters.joinToString(prefix = "<", postfix = "> ") {
                "${it.name} : ${it.bound.kotlinTypeName}"
            })
        }
        val parameterDeclarations = parameters.joinToString { "${it.name}: ${it.type}" }
        write("$className.${method.name}($parameterDeclarations): ${signature.returnType.kotlinTypeName} =\n")

        // original member invocation
        write("\t${method.name}(")
        parameters.forEachIndexed { i, p ->
            write(
                if (i == parameters.lastIndex)
                    "Action { it.${p.name}() }"
                else
                    "${p.name}, "
            )
        }
        write(")\n\n")
    }

    private fun kDocFor(className: String, method: MethodDescriptor, parameterTypeNames: List<String>): KDoc? {
        val signature = "$className.${method.name}(${parameterTypeNames.joinToString()})"
        return kDocProvider?.invoke(signature)?.apply {
            if (parameterNames.size != parameterTypeNames.size) {
                throw IllegalArgumentException("KDoc for `$signature` has wrong number of @param tags, expecting ${parameterTypeNames.size}, got ${parameterNames.size}")
            }
        }
    }

    private fun isGeneric(classNode: ClassNode): Boolean {
        val signature = classNode.signature?.let { ClassSignature.from(it) }
        return signature != null && signature.typeParameters.isNotEmpty()
    }

    private fun failedToWrite(className: String, method: MethodDescriptor, reason: Exception): Nothing =
        throw IllegalStateException(
            "Failed to write extension for `$className.${method.name}(${method.signature.parameters.joinToString { it.kotlinTypeName }})': ${reason.message}",
            reason)

    private fun kotlinTypeNameFor(type: JvmType) =
        if (type is GenericType && isActionClassType(type.definition))
            "${actionTargetTypeName(type)}.() -> Unit"
        else
            type.kotlinTypeName

    private fun actionTargetTypeName(actionType: GenericType) =
        fixPluginTypeName(actionType.arguments.single().kotlinTypeName)

    private fun fixPluginTypeName(kotlinTypeName: String): String =
        // Plugin is missing generic type arguments in PluginContainer.withId
        if (kotlinTypeName == "org.gradle.api.Plugin")
            "org.gradle.api.Plugin<*>"
        else
            kotlinTypeName

    private fun methodsRequiringExtensionIn(classNode: ClassNode) =
        classNode
            .methods
            .asSequence()
            .filterIsInstance<MethodNode>()
            .map { MethodDescriptor(it.name, it.signature ?: it.desc, it.access) }
            .filter(::conflictsWithExtension)

    private fun write(text: String) = writer.write(text)
}
