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

import gradlebuild.basics.decapitalize
import gradlebuild.binarycompatibility.isSynthetic
import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries
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
import javassist.Modifier
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
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

    fun getSince(member: JApiCompatibility): (KtFile) -> SinceTagStatus = { ktFile ->
        val ctMember = member.newCtMember
        val ctDeclaringClass = ctMember.declaringClass
        val declaringClassSince = ktFile.ktClassOf(ctDeclaringClass)?.getSince()
        when {
            ctMember is CtMethod && ctMember.isSynthetic -> SinceTagStatus.NotNeeded // synthetic members cannot have kdoc
            ctMember is CtClass -> ktFile.ktClassOf(ctMember).getSinceStatus()
            else -> when (ctMember) {
                is CtField -> ktFile.getSince(ctDeclaringClass, ctMember, fallback = declaringClassSince)
                is CtConstructor -> ktFile.getSince(ctDeclaringClass, ctMember, fallback = declaringClassSince)
                is CtMethod -> ktFile.getSince(ctDeclaringClass, ctMember, fallback = declaringClassSince)
                else -> error("Unsupported japicmp member type '${member::class}'")
            }
        }
    }

    private
    fun KtFile.getSince(declaringClass: CtClass, field: CtField, fallback: String?): SinceTagStatus =
        "${declaringClass.baseQualifiedKotlinName}.${field.name}".let { fqn ->
            collectDescendantsOfType<KtProperty>()
                .firstOrNull { it.fqName?.asString() == fqn }
                .getSinceStatus(fallback)
        }

    private
    fun KtFile.getSince(declaringClass: CtClass, constructor: CtConstructor, fallback: String?): SinceTagStatus {
        val classFqName = declaringClass.name
        val ctorParamTypes = constructor.parameterTypes
        return collectDescendantsOfType<KtConstructor<*>>()
            .firstOrNull { ktCtor ->
                val sameName = ktCtor.containingClassOrObject?.fqName?.asString() == classFqName
                val sameParamCount = ktCtor.valueParameters.size == ctorParamTypes.size
                val typeParameterBounds = (ktCtor.containingClassOrObject as? KtTypeParameterListOwner).typeParameterBounds
                val sameParamTypes = sameParamCount && ctorParamTypes.withIndex().all { (idx, paramType) ->
                    paramType.isLikelyEquivalentTo(ktCtor.valueParameters[idx].typeReference!!, typeParameterBounds)
                }
                sameName && sameParamCount && sameParamTypes
            }
            .getSinceStatus(fallback)
    }

    private
    fun KtFile.getSince(declaringClass: CtClass, method: CtMethod, fallback: String?): SinceTagStatus {
        val qualifiedBaseName = declaringClass.baseQualifiedKotlinName

        val functions = collectKtFunctionsFor(qualifiedBaseName, method)
        if (functions.isNotEmpty()) {
            return getSinceStatus(functions, fallback)
        }

        val properties = collectKtPropertiesFor(qualifiedBaseName, method)
        return getSinceStatus(properties, fallback)
    }

    private
    fun getSinceStatus(declarations: List<KtDeclaration>, fallback: String?): SinceTagStatus {
        val sinceTags = declarations.map { it.getSince() }
        return when {
            sinceTags.isEmpty() -> {
                fallback?.let { SinceTagStatus.Present(it) } ?: SinceTagStatus.Missing
            }

            sinceTags.all { it == sinceTags.first() } -> {
                (sinceTags.first() ?: fallback)?.let { SinceTagStatus.Present(it) } ?: SinceTagStatus.Missing
            }

            else -> {
                SinceTagStatus.Inconsistent(sinceTags)
            }
        }
    }

    private
    fun KtDeclaration?.getSinceStatus(fallback: String? = null): SinceTagStatus =
        (this?.getSince() ?: fallback)?.let { SinceTagStatus.Present(it) } ?: SinceTagStatus.Missing
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

    return collectDescendantsOfType { ktFunction ->
        // Name check
        val fqName = ktFunction.fqName ?: return@collectDescendantsOfType false
        if (fqName.parent().asString() != qualifiedBaseName || ktFunction.jvmName != method.name) {
            return@collectDescendantsOfType false
        }

        // Preliminary extension function check
        val allowJvmOverloads = ktFunction.hasJvmOverloads
        val extensionCandidate = couldBeExtensionFunction && ktFunction.receiverTypeReference != null &&
            method.firstParameterMatches(ktFunction.receiverTypeReference!!) &&
            ktFunction.acceptsValueParameterCount(paramCountWithReceiver, allowJvmOverloads)
        if (!(extensionCandidate || ktFunction.acceptsValueParameterCount(paramCount, allowJvmOverloads))) {
            return@collectDescendantsOfType false
        }
        val isVarargs = Modifier.isVarArgs(method.modifiers)
        val typeParameterBounds = ktFunction.typeParameterBounds

        // Parameter type check
        method.parameterTypes
            .asSequence()
            // Drop the receiver if present
            .drop(if (extensionCandidate) 1 else 0)
            .withIndex()
            .all {
                val ktParamType = ktFunction.valueParameters[it.index].typeReference!!
                it.value.isLikelyEquivalentTo(ktParamType, typeParameterBounds) ||
                    (isVarargs && it.value.componentType?.isLikelyEquivalentTo(ktParamType, typeParameterBounds) == true)
            }
    }
}


/**
 * Type parameters declared by this owner, mapped to their upper bound (null when unbounded).
 * Used to reconcile a source type parameter with its erased binary type.
 */
private
val KtTypeParameterListOwner?.typeParameterBounds: Map<String, KtTypeReference?>
    get() = this?.typeParameters.orEmpty().associate { it.name!! to it.extendsBound }

private
val KtFunction.typeParameterBounds: Map<String, KtTypeReference?>
    get() = (containingClassOrObject as? KtTypeParameterListOwner).typeParameterBounds +
        typeParameters.associate { it.name!! to it.extendsBound }


private
val KtFunction.jvmName: String?
    get() = annotationEntries.jvmName() ?: fqName?.shortName()?.asString()


private
val KtFunction.hasJvmOverloads: Boolean
    get() = annotationEntries.any { it.shortName?.asString() == "JvmOverloads" }


/**
 * Whether the function can back a JVM method with the given number of value parameters. With
 * `@JvmOverloads` the compiler also emits overloads that drop a trailing run of default-valued
 * parameters, so any prefix ending before such a run is accepted.
 */
private
fun KtFunction.acceptsValueParameterCount(count: Int, allowJvmOverloads: Boolean): Boolean =
    when {
        count == valueParameters.size -> true
        !allowJvmOverloads || count !in 0 until valueParameters.size -> false
        else -> valueParameters.drop(count).all { it.hasDefaultValue() }
    }


private
val KtProperty.getterJvmName: String?
    get() = annotationEntries.jvmName(AnnotationUseSiteTarget.PROPERTY_GETTER)


private
val KtProperty.setterJvmName: String?
    get() = annotationEntries.jvmName(AnnotationUseSiteTarget.PROPERTY_SETTER)


/**
 * Value of the `@JvmName` annotation with the given use-site target (none for a plain `@JvmName`),
 * or null when absent.
 */
private
fun List<KtAnnotationEntry>.jvmName(useSiteTarget: AnnotationUseSiteTarget? = null): String? =
    firstOrNull { it.shortName?.asString() == "JvmName" && it.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget }
        ?.valueArguments?.firstOrNull()
        ?.getArgumentExpression()
        ?.let { it as? KtStringTemplateExpression }
        ?.entries?.singleOrNull()?.text


private
fun KtFile.collectKtPropertiesFor(qualifiedBaseName: String, method: CtMethod): List<KtProperty> {
    val renamed = collectRenamedKtPropertiesFor(qualifiedBaseName, method)
    if (renamed.isNotEmpty()) {
        return renamed
    }

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
fun KtFile.collectRenamedKtPropertiesFor(qualifiedBaseName: String, method: CtMethod): List<KtProperty> {
    val paramCount = method.parameterTypes.size
    val returnsVoid = method.returnType.name == "void"

    return collectDescendantsOfType { ktProperty ->
        if (ktProperty.fqName?.parent()?.asString() != qualifiedBaseName) {
            return@collectDescendantsOfType false
        }
        val receiverParamCount = if (ktProperty.receiverTypeReference != null) 1 else 0
        val receiverMatches = ktProperty.receiverTypeReference
            ?.let { method.firstParameterMatches(it) } ?: true
        when (method.name) {
            ktProperty.getterJvmName -> paramCount == receiverParamCount && !returnsVoid && receiverMatches
            ktProperty.setterJvmName -> paramCount == receiverParamCount + 1 && returnsVoid && receiverMatches
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
        else -> error("Unsupported japicmp member type '${this::class}'")
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
        else -> error("Unsupported javassist member type '${this::class}'")
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
fun CtClass.isLikelyEquivalentTo(ktTypeReference: KtTypeReference, typeParameterBounds: Map<String, KtTypeReference?> = emptyMap()): Boolean {
    val ktTypeAsText = ktTypeReference.text
    if (ktTypeAsText.contains(" -> ")) {
        // This is a function of some sort
        return name.startsWith("kotlin.jvm.functions.Function")
    }

    val ktTypeRawName = ktTypeAsText
        .trimEnd('?') // nullability is not part of JVM types
        .substringBefore('<') // generics are not part of parameter types in JVM method signatures

    if (typeParameterBounds.containsKey(ktTypeRawName)) {
        return typeParameterBounds[ktTypeRawName]
            ?.let { isLikelyEquivalentTo(it) }
            ?: (name == "java.lang.Object")
    }

    val thisTypeAsKt = name.mapJavaTypeToKotlinType()
    return thisTypeAsKt.endsWith(ktTypeRawName)
}


private
fun KtFile.ktClassOf(member: CtClass) =
    collectDescendantsOfType<KtClassOrObject> { it.fqName?.asString() == member.name }.singleOrNull()


private
val SINCE_REGEX = Regex("""@since ([^\s]+)""")


fun KtDeclaration.getSince(): String? =
    docComment?.let { SINCE_REGEX.find(it.text)?.groupValues?.get(1) }


private
fun String.mapJavaTypeToKotlinType(): String {
    val javaTypeName = this
    return primitiveTypeStrings[javaTypeName] ?: collectionTypeStrings[javaTypeName] ?: javaTypeName
}


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


// See `org.gradle.kotlin.dsl.internal.sharedruntime.codegen.ApiTypeProviderKt.mappedTypeStrings`
private
val collectionTypeStrings =
    mapOf(
        "java.lang.Iterable" to "kotlin.collections.Iterable",
        "java.util.Iterator" to "kotlin.collections.Iterator",
        "java.util.ListIterator" to "kotlin.collections.ListIterator",
        "java.util.Collection" to "kotlin.collections.Collection",
        "java.util.List" to "kotlin.collections.List",
        "java.util.ArrayList" to "kotlin.collections.ArrayList",
        "java.util.Set" to "kotlin.collections.Set",
        "java.util.HashSet" to "kotlin.collections.HashSet",
        "java.util.LinkedHashSet" to "kotlin.collections.LinkedHashSet",
        "java.util.Map" to "kotlin.collections.Map",
        "java.util.Map\$Entry" to "kotlin.collections.Map.Entry",
        "java.util.HashMap" to "kotlin.collections.HashMap",
        "java.util.LinkedHashMap" to "kotlin.collections.LinkedHashMap"
    )
