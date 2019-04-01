/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility.sources

import japicmp.model.JApiCompatibility
import japicmp.model.JApiClass
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiHasSyntheticModifier
import japicmp.model.JApiMethod
import japicmp.model.SyntheticModifier

import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

import org.gradle.binarycompatibility.isKotlin
import org.gradle.binarycompatibility.jApiClass
import org.gradle.binarycompatibility.packageName
import org.gradle.binarycompatibility.metadata.KotlinMetadataQueries


internal
object KotlinSourceQueries {

    fun isOverrideMethod(method: JApiMethod): (KtFile) -> Boolean = { ktFile ->
        ktFile.kotlinDeclarationSatisfies(method.jApiClass, method) { ktMember ->
            ktMember.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    fun isSince(version: String, member: JApiCompatibility): (KtFile) -> Boolean = { ktFile ->
        val declaringClass = member.jApiClass
        when {
            member.isSynthetic -> true // skip
            member is JApiClass -> ktFile.ktClassOf(member)?.isDocumentedAsSince(version) == true
            ktFile.ktClassOf(declaringClass)?.isDocumentedAsSince(version) == true -> true
            else -> when (member) {
                is JApiField -> ktFile.isDocumentedAsSince(version, declaringClass, member)
                is JApiConstructor -> ktFile.isDocumentedAsSince(version, declaringClass, member)
                is JApiMethod -> ktFile.isDocumentedAsSince(version, declaringClass, member)
                else -> throw IllegalStateException("Unsupported japicmp member type '${member::class}'")
            }
        }
    }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: JApiClass, field: JApiField): Boolean =
        "${declaringClass.baseQualifiedKotlinName}.${field.name}".let { fqn ->
            collectDescendantsOfType<KtProperty> { it.fqName?.asString() == fqn }
                .singleOrNull()
                ?.isDocumentedAsSince(version) == true
        }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: JApiClass, constructor: JApiConstructor): Boolean {
        val classFqName = declaringClass.fullyQualifiedName
        val ctorParamTypes = constructor.parameters.map { it.type }
        return collectDescendantsOfType<KtConstructor<*>> { ktCtor ->
            val sameName = ktCtor.containingClassOrObject?.fqName?.asString() == classFqName
            val sameParamCount = ktCtor.valueParameters.size == ctorParamTypes.size
            val sameParamTypes = sameParamCount && ctorParamTypes.mapIndexed { idx, paramType -> paramType.endsWith(ktCtor.valueParameters[idx].typeReference!!.text) }.all { it }
            sameName && sameParamCount && sameParamTypes
        }.singleOrNull()?.isDocumentedAsSince(version) == true
    }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: JApiClass, method: JApiMethod): Boolean =
        kotlinDeclarationSatisfies(declaringClass, method) { declaration ->
            declaration.isDocumentedAsSince(version)
        }
}


private
fun KtFile.kotlinDeclarationSatisfies(declaringClass: JApiClass, method: JApiMethod, predicate: (KtDeclaration) -> Boolean): Boolean {
    val qualifiedBaseName = declaringClass.baseQualifiedKotlinName
    val properties = collectKtPropertiesFor(qualifiedBaseName, method)
    val functions = collectKtFunctionsFor(qualifiedBaseName, method)
    if (properties.isEmpty() && functions.isEmpty()) {
        return false
    }
    return properties.all(predicate) && functions.all(predicate)
}


private
fun KtFile.collectKtPropertiesFor(qualifiedBaseName: String, method: JApiMethod): List<KtProperty> {

    val hasGetterName = method.name.matches(propertyGetterNameRegex)
    val hasSetterName = method.name.matches(propertySetterNameRegex)
    val paramCount = method.parameters.size
    val returnsVoid = method.returnType.newReturnType == "void"

    val couldBeProperty = (hasGetterName && paramCount == 0 && !returnsVoid) || (hasSetterName && paramCount == 1 && returnsVoid)
    val couldBeExtensionProperty = (hasGetterName && paramCount == 1 && !returnsVoid) || (hasSetterName && paramCount == 2 && returnsVoid)

    return if (couldBeProperty || couldBeExtensionProperty) {

        val propertyName = method.name.drop(3).decapitalize()
        val propertyQualifiedName = "$qualifiedBaseName.$propertyName"

        collectDescendantsOfType { ktProperty ->
            ktProperty.fqName?.asString() == propertyQualifiedName && (
                couldBeExtensionProperty == (ktProperty.receiverTypeReference != null && method.firstParameterMatches(ktProperty.receiverTypeReference!!))
                    || couldBeProperty == (ktProperty.receiverTypeReference == null)
                )
        }
    } else {
        emptyList()
    }
}


private
fun KtFile.collectKtFunctionsFor(qualifiedBaseName: String, method: JApiMethod): List<KtFunction> {

    val paramCount = method.parameters.size
    val couldBeExtensionFunction = paramCount > 0
    val paramCountWithReceiver = paramCount - 1
    val functionFqName = "$qualifiedBaseName.${method.name}"

    return collectDescendantsOfType { ktFunction ->
        ktFunction.fqName?.asString() == functionFqName
            && ((couldBeExtensionFunction && ktFunction.receiverTypeReference != null && method.firstParameterMatches(ktFunction.receiverTypeReference!!) && ktFunction.valueParameters.size == paramCountWithReceiver)
            || ktFunction.valueParameters.size == paramCount)
    }
}


private
val propertyGetterNameRegex = "^get[A-Z].*$".toRegex()


private
val propertySetterNameRegex = "^set[A-Z].*$".toRegex()


internal
val JApiClass.isKotlin: Boolean
    get() = newClass.orNull()?.isKotlin ?: false


private
val JApiCompatibility.isSynthetic
    get() = this is JApiHasSyntheticModifier
        && syntheticModifier.newModifier.or(SyntheticModifier.NON_SYNTHETIC) == SyntheticModifier.SYNTHETIC


private
val JApiClass.baseQualifiedKotlinName: String
    get() =
        if (isKotlinFileFacadeClass) packageName
        else fullyQualifiedName


private
val JApiClass.isKotlinFileFacadeClass: Boolean
    get() = isKotlin && KotlinMetadataQueries.queryKotlinMetadata(
        newClass.get(),
        defaultResult = false,
        query = KotlinMetadataQueries.isKotlinFileFacadeClass()
    )


private
fun JApiMethod.firstParameterMatches(ktTypeReference: KtTypeReference): Boolean =
    parameters.isNotEmpty() && (primitiveTypeStrings[parameters[0].type] ?: parameters[0].type).endsWith(ktTypeReference.text)


private
fun KtFile.ktClassOf(member: JApiClass) =
    collectDescendantsOfType<KtClassOrObject> { it.fqName?.asString() == member.fullyQualifiedName }.singleOrNull()


private
inline fun <reified T : KtNamedDeclaration> KtFile.ktFqNamed(fqn: String) =
    collectDescendantsOfType<T> { it.fqName?.asString() == fqn }.singleOrNull()


private
fun KtDeclaration.isDocumentedAsSince(version: String) =
    docComment?.isSince(version) == true


private
fun KDoc.isSince(version: String) =
    text.contains("@since $version")


// TODO:kotlin-dsl dedupe with KotlinTypeStrings.primitiveTypeStrings
private
val primitiveTypeStrings =
    mapOf(
        "java.lang.Object" to "Any",
        "java.lang.String" to "String",
        "java.lang.Character" to "Char",
        "char" to "Char",
        "java.lang.Boolean" to "Boolean",
        "boolean" to "Boolean",
        "java.lang.Byte" to "Byte",
        "byte" to "Byte",
        "java.lang.Short" to "Short",
        "short" to "Short",
        "java.lang.Integer" to "Int",
        "int" to "Int",
        "java.lang.Long" to "Long",
        "long" to "Long",
        "java.lang.Float" to "Float",
        "float" to "Float",
        "java.lang.Double" to "Double",
        "double" to "Double"
    )
