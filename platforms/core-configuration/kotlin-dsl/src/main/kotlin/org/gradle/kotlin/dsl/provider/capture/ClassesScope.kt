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

import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * The compilation unit under transformation: every class read from the workspace, plus what those
 * classes are (the lambda classes, the scripts and their liftable surface, and the lambdas that capture
 * a script). Immutable data only — the analysis phases are free functions that read this and return
 * their own results; nothing writes back into it.
 */
internal class ClassesScope private constructor(
    val dir: Path,
    val classes: Map<InternalClassName, ClassNode>,
) {
    // FIXME: what about this$0 fields in regular non-lambda nested classes? BTW, do we want to cover them as well?
    val lambdaClassNames: Set<InternalClassName> =
        classes.values.filter { classNode -> classNode.fields.any { it.name == THIS0 } }.mapTo(mutableSetOf()) { it.internalName }

    // FIXME: why are we so sure about any target being the script? Should there be another predicate?
    val scripts: Map<InternalClassName, ScriptModel> = buildMap {
        for (classNode in classes.values) {
            for (field in classNode.fields) {
                if (field.name == THIS0) {
                    // A script is the target a this$0 points at — in-world and not itself a lambda class.
                    val scriptName = field.typeInternalName
                    if (scriptName in classes && scriptName !in lambdaClassNames) {
                        putIfAbsent(scriptName, scriptClassModelOf(classes.getValue(scriptName)))
                    }
                }
            }
        }
    }

    val capturingLambdas: Map<InternalClassName, Lambda> = buildMap {
        for (name in lambdaClassNames) {
            val classNode = classes.getValue(name)
            val this0 = classNode.fields.firstOrNull { it.name == THIS0 && !it.access.isStatic }
            val receiverType = this0?.typeInternalName
            // Only lambdas whose this$0 is a compiled script are our target — not those capturing an enclosing lambda.
            if (receiverType != null && receiverType in scripts) {
                val otherFields = classNode.fields.filter { it != this0 && !it.access.isStatic }
                put(name, Lambda(classNode, receiverType, otherFields))
            }
        }
    }

    companion object {
        fun readFromClassesDir(dir: Path): ClassesScope {
            val classes = buildMap {
                Files.walk(dir).use { paths ->
                    paths.filter { it.toString().endsWith(".class") }.forEach { file ->
                        val classNode = ClassNode(AsmConstants.ASM_LEVEL)
                        // Keep the compiler's stack-map frames (EXPAND_FRAMES) so the rewrite can preserve
                        // them on write, avoiding frame recomputation (and any class loading it would need).
                        ClassReader(Files.readAllBytes(file)).accept(classNode, ClassReader.EXPAND_FRAMES)
                        put(classNode.internalName, classNode)
                    }
                }
            }
            return ClassesScope(dir, classes)
        }
    }
}

/** Models a script's liftable val/var surface by scanning its own fields (an immutable result). */
private fun scriptClassModelOf(scriptClass: ClassNode): ScriptModel {
    val valGetterToField = mutableMapOf<String, String>()
    val valFieldDesc = mutableMapOf<String, String>()
    val varFields = mutableSetOf<String>()
    val varFieldDesc = mutableMapOf<String, String>()
    val varMethodToField = mutableMapOf<String, String>()
    for (field in scriptClass.fields) {
        if (field.access.isStatic || field.name.startsWith("\$\$") || field.name == THIS0 || field.name == "host") {
            continue
        }
        if (field.access.isFinal) {
            valGetterToField[field.name.getterName()] = field.name
            valFieldDesc[field.name] = field.desc
        } else {
            varFields.add(field.name)
            varFieldDesc[field.name] = field.desc
            varMethodToField[field.name.getterName()] = field.name
            varMethodToField[field.name.setterName()] = field.name
        }
    }
    val convertibleVars = varFields.filterTo(mutableSetOf()) { isVarConvertible(scriptClass, it) }
    return ScriptModel(scriptClass.internalName, valGetterToField, valFieldDesc, varFields, varFieldDesc, varMethodToField, convertibleVars)
}

/**
 * A var can back a shared cell only if its state is confined to a trivial accessor pair plus its
 * initializer: exactly one field read in the getter, one write in the setter (to `this`), and at most
 * one write in `<init>` (also to `this`). Any other touch of the field means the value can be observed
 * or mutated outside those accessors, so rerouting through a cell would be unsound.
 */
private fun isVarConvertible(scriptClass: ClassNode, varField: String): Boolean {
    val getter = scriptClass.findMethod(varField.getterName()) ?: return false
    val setter = scriptClass.findMethod(varField.setterName()) ?: return false
    var initPuts = 0
    var getterGets = 0
    var setterPuts = 0
    for (method in scriptClass.methods) {
        for (insn in method.instructions) {
            if (insn !is FieldInsnNode || insn.owner != scriptClass.name || insn.name != varField) {
                // FIXME: should we make a stricter check for non-trivial accessors? Like, no other instructions than field operation and return?
                continue
            }
            when {
                method === getter && insn.opcode == Opcodes.GETFIELD -> getterGets++
                insn.opcode == Opcodes.PUTFIELD && (method === setter || method.name == "<init>") -> {
                    if (!insn.writesToThis()) return false
                    if (method === setter) setterPuts++ else initPuts++
                }
                else -> return false // any other access to the var -> not convertible
            }
        }
    }
    // initPuts may be 0 when the initializer is the JVM default (Kotlin elides `= 0` / `= null`);
    // the synthesized empty cell then supplies that default.
    return initPuts <= 1 && getterGets == 1 && setterPuts == 1
}
