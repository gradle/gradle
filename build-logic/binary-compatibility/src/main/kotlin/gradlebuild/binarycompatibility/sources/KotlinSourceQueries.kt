/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.sources

import gradlebuild.binarycompatibility.isSynthetic
import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries
import gradlebuild.decapitalize
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import javassist.CtBehavior
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMember
import javassist.CtMethod
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject


internal
object KotlinSourceQueries {

    fun isOverrideMethod(method: JApiMethod): (KtFile) -> Boolean = { ktFile ->
        val ctMethod = method.newMethod.get()
        ktFile.kotlinDeclarationSatisfies(ctMethod.declaringClass, ctMethod) { ktMember ->
            ktMember.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    fun isSince(version: String, member: JApiCompatibility): (KtFile) -> Boolean = { ktFile ->
        val ctMember = member.newCtMember
        val ctDeclaringClass = ctMember.declaringClass
        when {
            ctMember is CtMethod && ctMember.isSynthetic -> true // synthetic members cannot have kdoc
            ctMember is CtClass -> ktFile.ktClassOf(ctMember)?.isDocumentedAsSince(version) == true
            ktFile.ktClassOf(ctDeclaringClass)?.isDocumentedAsSince(version) == true -> true
            else -> when (ctMember) {
                is CtField -> ktFile.isDocumentedAsSince(version, ctDeclaringClass, ctMember)
                is CtConstructor -> ktFile.isDocumentedAsSince(version, ctDeclaringClass, ctMember)
                is CtMethod -> ktFile.isDocumentedAsSince(version, ctDeclaringClass, ctMember)
                else -> throw IllegalStateException("Unsupported japicmp member type '${member::class}'")
            }
        }
    }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: CtClass, field: CtField): Boolean =
        "${declaringClass.baseQualifiedKotlinName}.${field.name}".let { fqn ->
            collectDescendantsOfType<KtProperty>()
                .firstOrNull { it.fqName?.asString() == fqn }
                ?.isDocumentedAsSince(version) == true
        }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: CtClass, constructor: CtConstructor): Boolean {
        val classFqName = declaringClass.name
        val ctorParamTypes = constructor.parameterTypes.map { it.name }
        return collectDescendantsOfType<KtConstructor<*>>()
            .firstOrNull { ktCtor ->
                val sameName = ktCtor.containingClassOrObject?.fqName?.asString() == classFqName
                val sameParamCount = ktCtor.valueParameters.size == ctorParamTypes.size
                val sameParamTypes = sameParamCount && ctorParamTypes.mapIndexed { idx, paramType -> paramType.endsWith(ktCtor.valueParameters[idx].typeReference!!.text) }.all { it }
                sameName && sameParamCount && sameParamTypes
            }
            ?.isDocumentedAsSince(version) == true
    }

    private
    fun KtFile.isDocumentedAsSince(version: String, declaringClass: CtClass, method: CtMethod): Boolean =
        kotlinDeclarationSatisfies(declaringClass, method) { declaration ->
            declaration.isDocumentedAsSince(version)
        }
}


private
fun KtFile.kotlinDeclarationSatisfies(declaringClass: CtClass, method: CtMethod, predicate: (KtDeclaration) -> Boolean): Boolean {

    val qualifiedBaseName = declaringClass.baseQualifiedKotlinName

    val functions = collectKtFunctionsFor(qualifiedBaseName, method)
    if (functions.isNotEmpty()) {
        return functions.all(predicate)
    }

    val properties = collectKtPropertiesFor(qualifiedBaseName, method)
    return properties.isNotEmpty() && properties.all(predicate)
}


private
fun KtFile.collectKtFunctionsFor(qualifiedBaseName: String, method: CtMethod): List<KtFunction> {

    val paramCount = method.parameterTypes.size
    val couldBeExtensionFunction = paramCount > 0
    val paramCountWithReceiver = paramCount - 1
    val functionFqName = "$qualifiedBaseName.${method.name}"

    return collectDescendantsOfType { ktFunction ->
        // Name check
        if (ktFunction.fqName?.asString() != functionFqName) {
            return@collectDescendantsOfType false
        }

        // Preliminary extension function check
        val extensionCandidate = couldBeExtensionFunction && ktFunction.receiverTypeReference != null &&
            method.firstParameterMatches(ktFunction.receiverTypeReference!!) &&
            ktFunction.valueParameters.size == paramCountWithReceiver
        if (!(extensionCandidate || ktFunction.valueParameters.size == paramCount)) {
            return@collectDescendantsOfType false
        }

        // Parameter type check
        method.parameterTypes
            .asSequence()
            // Drop the receiver if present
            .drop(if (extensionCandidate) 1 else 0)
            .withIndex()
            .all<IndexedValue<CtClass>> {
                val ktParamType = ktFunction.valueParameters[it.index].typeReference!!
                it.value.isLikelyEquivalentTo(ktParamType)
            }
    }
}


private
fun KtFile.collectKtPropertiesFor(qualifiedBaseName: String, method: CtMethod): List<KtProperty> {

    val hasGetGetterName = method.name.matches(propertyGetterNameRegex)
    val hasIsGetterName = method.name.matches(propertyIsGetterNameRegex)
    val hasGetterName = hasGetGetterName || hasIsGetterName
    val hasSetterName = method.name.matches(propertySetterNameRegex)
    val paramCount = method.parameterTypes.size
    val returnsVoid = method.returnType.name == "void"

    val couldBeProperty =
        (hasGetterName && paramCount == 0 && !returnsVoid) || (hasSetterName && paramCount == 1 && returnsVoid)

    val couldBeExtensionProperty =
        (hasGetterName && paramCount == 1 && !returnsVoid) || (hasSetterName && paramCount == 2 && returnsVoid)

    if (!couldBeProperty && !couldBeExtensionProperty) {
        return emptyList()
    }

    val propertyJavaType =
        if (hasGetterName) method.returnType.name
        else method.parameterTypes.last().name

    val isBoolean =
        primitiveTypeStrings[propertyJavaType] == Boolean::class.simpleName

    val propertyNames =
        if (hasIsGetterName) listOf(method.name)
        else {
            val prefixRemoved = method.name.drop(3)
            if (hasSetterName && isBoolean) listOf("is$prefixRemoved", prefixRemoved.decapitalize())
            else listOf(prefixRemoved.decapitalize())
        }

    val propertyQualifiedNames =
        propertyNames.map { "$qualifiedBaseName.$it" }

    return collectDescendantsOfType { ktProperty ->
        when {
            ktProperty.fqName?.asString() !in propertyQualifiedNames -> false
            couldBeExtensionProperty -> {
                ktProperty.receiverTypeReference != null &&
                    method.firstParameterMatches(ktProperty.receiverTypeReference!!)
            }
            couldBeProperty -> {
                ktProperty.receiverTypeReference == null
            }
            else -> false
        }
    }
}


private
val propertyGetterNameRegex = "^get[A-Z].*$".toRegex()


private
val propertyIsGetterNameRegex = "^is[A-Z].*$".toRegex()


private
val propertySetterNameRegex = "^set[A-Z].*$".toRegex()


private
val JApiCompatibility.newCtMember: CtClassOrCtMember
    get() = when (this) {
        is JApiClass -> newClass.get()
        is JApiConstructor -> newConstructor.get()
        is JApiField -> newFieldOptional.get()
        is JApiMethod -> newMethod.get()
        else -> throw IllegalStateException("Unsupported japicmp member type '${this::class}'")
    }


/**
 * [CtClass] or [CtMember].
 */
private
typealias CtClassOrCtMember = Any


private
val CtClassOrCtMember.declaringClass: CtClass
    get() = when (this) {
        is CtClass -> declaringClass ?: this
        is CtMember -> declaringClass
        else -> throw IllegalStateException("Unsupported javassist member type '${this::class}'")
    }


private
val CtClass.baseQualifiedKotlinName: String
    get() =
        if (isKotlinFileFacadeClass) packageName
        else name


private
val CtClass.isKotlinFileFacadeClass: Boolean
    get() = KotlinMetadataQueries.isKotlinFileFacadeClass(this)


private
fun CtBehavior.firstParameterMatches(ktTypeReference: KtTypeReference): Boolean =
    parameterTypes.firstOrNull()?.isLikelyEquivalentTo(ktTypeReference) ?: false


private
fun CtClass.isLikelyEquivalentTo(ktTypeReference: KtTypeReference): Boolean {
    if (ktTypeReference.text.contains(" -> ")) {
        // This is a function of some sort
        return name.startsWith("kotlin.jvm.functions.Function")
    }
    return (primitiveTypeStrings[name] ?: name).endsWith(ktTypeReference.text.substringBefore('<'))
}


private
fun KtFile.ktClassOf(member: CtClass) =
    collectDescendantsOfType<KtClassOrObject> { it.fqName?.asString() == member.name }.singleOrNull()


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
