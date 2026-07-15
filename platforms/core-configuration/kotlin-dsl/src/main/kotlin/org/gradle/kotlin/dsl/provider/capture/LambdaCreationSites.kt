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

package org.gradle.kotlin.dsl.provider.capture

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Finds every place each capturing lambda is instantiated and whether the site pushes the script as a
 * clean, removable suffix, producing one [LambdaSites] per lambda.
 */
internal fun scanLambdaCreationSites(
    classes: Map<InternalClassName, ClassNode>,
    scriptNames: Set<InternalClassName>,
    lambdaClassNames: Set<InternalClassName>,
): Map<InternalClassName, LambdaSites> {
    val sitesByLambda = linkedMapOf<InternalClassName, MutableList<LambdaInstantiationSite>>()
    val cleanByLambda = mutableMapOf<InternalClassName, Boolean>()
    for (classNode in classes.values) {
        val isInImmediateScriptClass = classNode.internalName in scriptNames
        for (method in classNode.methods) {
            for (init in method.instructions.constructorCalls()) {
                val newInstanceClass = init.ownerInternalClassName
                if (newInstanceClass !in lambdaClassNames)
                    continue

                sitesByLambda.getOrPut(newInstanceClass) { mutableListOf() }
                    .add(LambdaInstantiationSite(classNode, method, init, isInImmediateScriptClass))
                val clean = isCleanScriptPushSuffix(init, isInImmediateScriptClass, classNode, scriptNames)
                cleanByLambda[newInstanceClass] = (cleanByLambda[newInstanceClass] ?: true) && clean
                if (!clean) logSkip(newInstanceClass, "malformed creation site in ${classNode.name}.${method.name}")
            }
        }
    }
    return lambdaClassNames.associateWith { name ->
        LambdaSites(sitesByLambda[name].orEmpty(), cleanByLambda[name] ?: true)
    }
}

/** The script push must be exactly `aload_0` (script context) or `aload_0; getfield this$0` (lambda context). */
private fun isCleanScriptPushSuffix(
    init: MethodInsnNode,
    isInScriptContext: Boolean,
    enclosing: ClassNode,
    scriptNames: Set<InternalClassName>,
): Boolean {
    val args = Type.getArgumentTypes(init.desc)
    if (args.isEmpty() || args.last().internalClassName !in scriptNames) {
        return false
    }
    val scriptPush = init.previousReal()
    if (isInScriptContext) {
        return scriptPush.isAload0()
    }
    if (scriptPush !is FieldInsnNode ||
        scriptPush.opcode != Opcodes.GETFIELD ||
        scriptPush.name != THIS0 ||
        scriptPush.owner != enclosing.name
    ) {
        return false
    }
    return scriptPush.previousReal().isAload0()
}
