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

package org.gradle.internal.declarativedsl.documentation

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName


/**
 * Projects a parameter type reference to its catalog key fragment in JVM-binary form.
 */
fun paramTypeFragment(ref: DataTypeRef, schema: AnalysisSchema): String = when (ref) {
    is DataTypeRef.Type -> primitiveFragment(ref.dataType)
    is DataTypeRef.Name -> schema.javaTypeNameOf(ref.fqName)
    is DataTypeRef.NameWithArgs -> {
        val signature = schema.genericSignaturesByFqName[ref.fqName]
            ?: error("Unknown generic signature: ${ref.fqName.qualifiedName}")
        if (signature is DataType.VarargSignature) {
            val element = (ref.typeArguments.single() as DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument).type
            paramTypeFragment(element, schema) + "[]"
        } else {
            signature.javaTypeName
        }
    }
}


/**
 * Builds the catalog key for a member function: `simpleName(<frag1>,<frag2>,...)` with no whitespace.
 */
fun functionKey(simpleName: String, parameters: List<DataParameter>, schema: AnalysisSchema): String =
    parameters.joinToString(separator = ",", prefix = "$simpleName(", postfix = ")") {
        paramTypeFragment(it.type, schema)
    }


private fun primitiveFragment(type: DataType.PrimitiveType): String = when (type) {
    is DataType.IntDataType -> "int"
    is DataType.LongDataType -> "long"
    is DataType.BooleanDataType -> "boolean"
    is DataType.StringDataType -> "java.lang.String"
    is DataType.UnitType -> "void"
    is DataType.TypeVariableUsage -> "T" // TODO: preserve the real name or erasure
    is DataType.NullType -> error("Unsupported primitive type for catalog key fragment: $type")
}


private fun AnalysisSchema.javaTypeNameOf(fqn: FqName): String =
    dataClassTypesByFqName[fqn]?.javaTypeName
        ?: genericSignaturesByFqName[fqn]?.javaTypeName
        ?: error("No JVM type name registered for ${fqn.qualifiedName}")
