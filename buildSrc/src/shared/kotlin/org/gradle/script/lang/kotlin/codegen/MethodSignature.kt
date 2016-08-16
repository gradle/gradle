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

package org.gradle.script.lang.kotlin.codegen

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

data class MethodSignature(
    val parameters: List<JvmType>,
    val returnType: JvmType,
    val typeParameters: List<TypeParameter> = emptyList()) {

    companion object {
        fun from(signature: String): MethodSignature {

            val parameters = arrayListOf<JvmType>()
            val typeParameters = arrayListOf<TypeParameter>()
            var returnType: JvmType? = null
            var nextTypeParameterName: String? = null

            SignatureReader(signature).accept(object : SignatureVisitor(Opcodes.ASM5) {

                override fun visitFormalTypeParameter(name: String) {
                    nextTypeParameterName = name
                }

                override fun visitClassBound(): SignatureVisitor {
                    return visitInterfaceBound()
                }

                override fun visitInterfaceBound(): SignatureVisitor {
                    return JvmTypeBuilder {
                        typeParameters.add(TypeParameter(nextTypeParameterName!!, it))
                    }
                }

                override fun visitParameterType(): SignatureVisitor {
                    return JvmTypeBuilder {
                        parameters.add(it)
                    }
                }

                override fun visitReturnType(): SignatureVisitor {
                    return JvmTypeBuilder() {
                        returnType = it
                    }
                }
            })
            return MethodSignature(parameters, returnType!!, typeParameters)
        }
    }
}

data class TypeParameter(val name: String, val bound: JvmType)

sealed class JvmType {
    abstract val kotlinTypeName: String
}

data class PrimitiveType(val descriptor: Char) : JvmType() {

    override val kotlinTypeName: String
        get() =
        when (descriptor) {
            'V' -> "Unit"
            'Z' -> "Boolean"
            'C' -> "Char"
            'B' -> "Byte"
            'S' -> "Short"
            'I' -> "Int"
            'F' -> "Float"
            'J' -> "Long"
            'D' -> "Double"
            else -> throw IllegalStateException()
        }
}

data class ClassType(val internalName: String) : JvmType() {

    override val kotlinTypeName: String
        get() = when (internalName) {
            "java/lang/Object" -> "Any"
            "java/lang/String" -> "String"
            "java/lang/Iterable" -> "Iterable"
            else -> internalName.replace('/', '.')
        }
}

data class ArrayType(val elementType: JvmType) : JvmType() {

    override val kotlinTypeName: String
        get() = "Array<${elementType.kotlinTypeName}>"
}

data class GenericType(val definition: JvmType, val arguments: List<JvmType>) : JvmType() {

    override val kotlinTypeName: String
        get() = definition.kotlinTypeName + arguments.joinToString(prefix = "<", postfix = ">") { it.kotlinTypeName }
}

data class GenericTypeVariable(val name: String) : JvmType() {

    override val kotlinTypeName: String
        get() = name
}

class WildcardType : JvmType() {

    companion object {
        val Instance = WildcardType()
    }

    override val kotlinTypeName: String
        get() = "*"
}

/**
 * <li><i>TypeSignature</i> = <tt>visitBaseType</tt> |
 * <tt>visitTypeVariable</tt> | <tt>visitArrayType</tt> | (
 * <tt>visitClassType</tt> <tt>visitTypeArgument</tt>* (
 * <tt>visitInnerClassType</tt> <tt>visitTypeArgument</tt>* )* <tt>visitEnd</tt>
 * ) )</li>
 */
internal class JvmTypeBuilder(val onEnd: (JvmType) -> Unit) : SignatureVisitor(Opcodes.ASM5) {
    var type: JvmType? = null
    val typeArguments = arrayListOf<JvmType>()

    override fun visitBaseType(descriptor: Char) {
        assert(type == null)
        onEnd(PrimitiveType(descriptor))
    }

    override fun visitTypeVariable(name: String) {
        assert(type == null)
        onEnd(GenericTypeVariable(name))
    }

    override fun visitArrayType(): SignatureVisitor {
        return JvmTypeBuilder {
            onEnd(ArrayType(it))
        }
    }

    override fun visitInnerClassType(name: String) {
        TODO()
    }

    override fun visitClassType(name: String) {
        assert(type == null)
        type = ClassType(name)
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
        return JvmTypeBuilder {
            typeArguments.add(it)
        }
    }

    override fun visitTypeArgument() {
        typeArguments.add(WildcardType.Instance)
    }

    override fun visitEnd() {
        onEnd(when {
            typeArguments.isNotEmpty() -> GenericType(type!!, typeArguments)
            else -> type!!
        })
    }
}

