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

package org.gradle.internal.declarativedsl.schemaUtils

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.StarProjection
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.TypeArgumentInternal
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter


inline fun <reified T> AnalysisSchema.findTypeFor(): DataClass? =
    findTypeFor(T::class.java)


fun AnalysisSchema.findTypeFor(javaClass: Class<*>): DataClass? =
    dataClassTypesByFqName.values
        .filterIsInstance<DataClass>()
        .find { it.name.qualifiedName == javaClass.kotlin.qualifiedName }


inline fun <reified T> AnalysisSchema.typeFor(): DataClass =
    typeFor(T::class.java)


fun AnalysisSchema.typeFor(javaClass: Class<*>): DataClass =
    findTypeFor(javaClass)
        ?: throw NoSuchElementException("no type found in the schema for '${javaClass.name}'")


fun AnalysisSchema.findType(predicate: (DataClass) -> Boolean): DataClass? =
    dataClassTypesByFqName.values
        .filterIsInstance<DataClass>()
        .find(predicate)


fun DataClass.findPropertyNamed(name: String): TypedMember.TypedProperty? =
    properties.find { it.name == name }?.let { TypedMember.TypedProperty(this, it) }


fun DataClass.propertyNamed(name: String): TypedMember.TypedProperty =
    findPropertyNamed(name)
        ?: throw NoSuchElementException("no property named $name was found in the type $this")


fun AnalysisSchema.findPropertyFor(propertyReference: KProperty1<*, *>): TypedMember.TypedProperty? {
    val receiverType = propertyReference.instanceParameter?.type?.classifier as? KClass<*>
        ?: return null
    val receiverDataClass = findTypeFor(receiverType.java)
        ?: return null
    return receiverDataClass.findPropertyNamed(propertyReference.name)
}


fun AnalysisSchema.propertyFor(propertyReference: KProperty1<*, *>): TypedMember.TypedProperty {
    val receiverType = propertyReference.instanceParameter?.type?.classifier as? KClass<*>
        ?: throw NoSuchElementException("the property $propertyReference has no receiver type, can't find a match in the schema")
    val receiverDataClass = findTypeFor(receiverType.java)
        ?: throw NoSuchElementException("the receiver type $receiverType for $propertyReference is not in the schema")
    return receiverDataClass.propertyNamed(propertyReference.name)
}


fun DataClass.hasFunctionNamed(name: String): Boolean =
    memberFunctions.any { it.simpleName == name }


/**
 * A utility that finds an unambiguous [SchemaMemberFunction] by the [name] in the [DataClass.memberFunctions].
 *
 * To locate a function among multiple overloads, use [DataClass] APIs instead.
 */
fun DataClass.singleFunctionNamed(name: String): TypedMember.TypedFunction =
    TypedMember.TypedFunction(this, memberFunctions.single { it.simpleName == name })


fun AnalysisSchema.functionFor(functionReference: KFunction<*>): TypedMember.TypedFunction {
    val functions = findFunctionsFor(functionReference)
    return when (functions.size) {
        0 -> throw NoSuchElementException("no schema function found that matches $functionReference")
        1 -> functions.single()
        else -> throw NoSuchElementException("multiple schema functions match the $functionReference: ${functions.joinToString("\n") { "* $it" }}")
    }
}


fun AnalysisSchema.findFunctionFor(functionReference: KFunction<*>): TypedMember.TypedFunction? {
    val functions = findFunctionsFor(functionReference)
    return when (functions.size) {
        1 -> return functions.single()
        else -> null
    }
}


private
fun AnalysisSchema.findFunctionsFor(functionReference: KFunction<*>): List<TypedMember.TypedFunction> {
    require(functionReference.extensionReceiverParameter == null) { "extension function $functionReference cannot be matched in the schema" }

    val receiverType = functionReference.instanceParameter?.type?.classifier as? KClass<*>
        ?: throw NoSuchElementException("the function $functionReference has no receiver type, can't find a match in the schema")
    val receiverDataClass = findTypeFor(receiverType.java)
        ?: throw NoSuchElementException("the receiver type $receiverType for $functionReference is not in the schema")

    val functionReferenceParameters = functionReference.parameters.filter { it != functionReference.instanceParameter }
    val matchingSchemaFunctions = receiverDataClass.memberFunctions.filter { schemaFunction ->
        schemaFunction.simpleName == functionReference.name &&
            // sub-setting it this way, because there might also be a lambda parameter in the KFunction, and we don't have a way to understand it here
            schemaFunction.parameters.all { schemaParam ->
                functionReferenceParameters.any { kParam ->
                    kParam.name == schemaParam.name && findTypeFor(kParam.type)?.matchesRef(schemaParam.type) == true
                }
            }
    }
    return matchingSchemaFunctions.map { TypedMember.TypedFunction(receiverDataClass, it) }
}


private
fun AnalysisSchema.findTypeFor(kType: KType): DataType.ClassDataType? {
    val classifier = kType.classifier
    return if (classifier is KClass<*>) {
        if (classifier.typeParameters.isEmpty()) {
            findTypeFor(classifier.java)
        } else {
            if (kType.arguments.all { it == KTypeProjection.STAR || it.variance == KVariance.INVARIANT && it.type != null }) {
                genericInstantiationsByFqName[DefaultFqName.parse(classifier.qualifiedName ?: return null)]
                    ?.get(kType.arguments.map { typeArgument(it)?: return null })
            } else null
        }
    } else null
}

private fun AnalysisSchema.typeArgument(typeProjection: KTypeProjection): TypeArgument? {
    return if (typeProjection == KTypeProjection.STAR)
        TypeArgumentInternal.DefaultStarProjection()
    else TypeArgumentInternal.DefaultConcreteTypeArgument(findTypeFor(typeProjection.type ?: return null)?.ref ?: return null)
}

private
fun DataType.matchesRef(ref: DataTypeRef): Boolean = when (ref) {
    is DataTypeRef.Name -> this is DataClass && this.name == ref.fqName
    is DataTypeRef.Type -> this == ref.dataType
    is DataTypeRef.NameWithArgs -> this is DataType.ParameterizedTypeInstance &&
        this.typeSignature.name == ref.fqName &&
        typeArguments.zip(ref.typeArguments).all { (thisArg, otherArg) -> thisArg.isEquivalentTypeArgument(otherArg) }
}

private
fun DataTypeRef.isRefToSameType(other: DataTypeRef): Boolean = when (this) {
    is DataTypeRef.Type -> other is DataTypeRef.Type && other.dataType == dataType || dataType.matchesRef(other)
    is DataTypeRef.Name -> other is DataTypeRef.Name && fqName == other.fqName || other is DataTypeRef.Type && other.dataType.matchesRef(this)
    is DataTypeRef.NameWithArgs -> other is DataTypeRef.NameWithArgs && other.fqName == fqName && other.typeArguments.zip(typeArguments).all { (thisArg, otherArg) ->
        thisArg.isEquivalentTypeArgument(otherArg)
    }
}

private fun TypeArgument.isEquivalentTypeArgument(otherArg: TypeArgument) = when (this) {
    is StarProjection -> otherArg is StarProjection
    is ConcreteTypeArgument -> otherArg is ConcreteTypeArgument && type.isRefToSameType(otherArg.type)
}
