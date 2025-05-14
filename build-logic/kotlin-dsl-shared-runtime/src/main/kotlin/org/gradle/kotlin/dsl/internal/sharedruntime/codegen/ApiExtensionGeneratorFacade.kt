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

package org.gradle.kotlin.dsl.internal.sharedruntime.codegen

import org.gradle.kotlin.dsl.internal.sharedruntime.support.appendReproducibleNewLine
import java.io.File


/**
 * Helper for reflective usage by `KotlinExtensionsForGradleApiFacade`.
 */
class ApiExtensionGeneratorFacade {
    @Suppress("UNCHECKED_CAST")
    fun generate(parameters: Map<String, Any>) {
        generateKotlinDslApiExtensionsSourceTo(
            parameters["asmLevel"] as Int,
            parameters["platformClassLoader"] as ClassLoader,
            parameters["incubatingAnnotationTypeDescriptor"] as String,
            parameters["outputDirectory"] as File,
            parameters["packageName"] as String,
            parameters["sourceFilesBaseName"] as String,
            parameters["hashTypeSourceName"] as java.util.function.Function<String, String>,
            parameters["classPath"] as List<File>,
            parameters["classPathDependencies"] as List<File>,
            parameters["apiSpec"] as java.util.function.Function<String, Boolean>,
            parameters["functionSinceSupplier"] as java.util.function.Function<String, String?>,
        )
    }
}


private
fun interface ApiSpec {
    fun isApi(sourceName: String): Boolean
}


/**
 * Generate source file with Kotlin extensions enhancing the given api for the Gradle Kotlin DSL.
 *
 * @param asmLevel ASM level
 * @param outputDirectory the directory where the generated sources will be written
 * @param packageName the name of the package where the generated members will be added
 * @param sourceFilesBaseName the base name for generated source files
 * @param classPath the api classpath elements
 * @param classPathDependencies the api classpath dependencies
 * @param apiSpec the api include/exclude spec
 * @param sinceSupplier the api functions `@since` values for binary signatures
 *
 * @return the list of generated source files
 */
fun generateKotlinDslApiExtensionsSourceTo(
    asmLevel: Int,
    platformClassLoader: ClassLoader,
    incubatingAnnotationTypeDescriptor: String,
    outputDirectory: File,
    packageName: String,
    sourceFilesBaseName: String,
    hashTypeSourceName: java.util.function.Function<String, String>,
    classPath: List<File>,
    classPathDependencies: List<File>,
    apiSpec: java.util.function.Function<String, Boolean>,
    sinceSupplier: java.util.function.Function<String, String?>,
): List<File> =

    apiTypeProviderFor(
        asmLevel,
        platformClassLoader,
        incubatingAnnotationTypeDescriptor,
        classPath,
        classPathDependencies
    ).use { api ->

        val extensionsPerTarget =
            kotlinDslApiExtensionsDeclarationsFor(
                api,
                { sinceSupplier.apply(it) },
                { apiSpec.apply(it) }
            ).groupedByTarget()

        val sourceFiles =
            ArrayList<File>(extensionsPerTarget.size)

        val packageDir =
            outputDirectory.resolve(packageName.replace('.', File.separatorChar))

        fun sourceFile(name: String) =
            packageDir.resolve(name).also { sourceFiles.add(it) }

        packageDir.mkdirs()

        for ((targetType, extensionsSubset) in extensionsPerTarget) {
            writeExtensionsTo(
                sourceFile("${sourceFilesBaseName}_${hashTypeSourceName.apply(targetType.sourceName)}.kt"),
                packageName,
                targetType,
                extensionsSubset
            )
        }

        sourceFiles
    }


private
fun Sequence<KotlinExtensionFunction>.groupedByTarget(): Map<ApiType, List<KotlinExtensionFunction>> =
    groupBy { it.targetType }


private
fun writeExtensionsTo(outputFile: File, packageName: String, targetType: ApiType, extensions: List<KotlinExtensionFunction>): Unit =
    outputFile.bufferedWriter().use { writer ->
        writer.write(fileHeaderFor(packageName, isIncubatingFileClass(targetType, extensions)))
        writer.write("\n")
        extensions.forEach {
            writer.write("\n${it.toKotlinString()}")
        }
    }


/**
 * An implicit file class like `AbcKt` that Kotlin generates is also part of the public API,
 * and it should be appropriately marked with `@file:Incubating`.
 *
 * When the target method of an extension functions is de-incubated, removing at a later point is a breaking change.
 * The corresponding extension function gets automatically de-incubated during source generation.
 * But the same applies to the implicit file class, and it has to be de-incubated when any
 * extension function it contains is de-incubated.
 */
private
fun isIncubatingFileClass(targetType: ApiType, extensions: List<KotlinExtensionFunction>): Boolean {
    return targetType.isIncubating || extensions.all { it.isIncubating }
}


private
fun kotlinDslApiExtensionsDeclarationsFor(
    api: ApiTypeProvider,
    sinceSupplier: (String) -> String?,
    apiSpec: ApiSpec
): Sequence<KotlinExtensionFunction> =

    api.allTypes()
        .filter { type -> type.isPublic && apiSpec.isApi(type.sourceName) }
        .flatMap { type -> kotlinExtensionFunctionsFor(type, sinceSupplier) }
        .distinctBy(::signatureKey)


private
fun signatureKey(extension: KotlinExtensionFunction): List<Any> = extension.run {
    listOf(targetType.sourceName, name) +
        parameters.flatMap { apiTypeKey(it.type) }
}


private
fun apiTypeKey(usage: ApiTypeUsage): List<Any> = usage.run {
    listOf(sourceName, isNullable, isRaw, variance) +
        typeArguments.flatMap(::apiTypeKey) +
        bounds.flatMap(::apiTypeKey)
}


// TODO Policy for extensions with reified generics
//
// Goals
// - make the dsl predictable
// - prevent ambiguous overload situations
//
// Rules
// 1. an extension should either require no type parameters, a single reifeid type parameter, at call site
// 2. all type parameters must all be reifeid or values (TypeOf, KClass or Class)
// 3. when overloading, prefer TypeOf over Class
// 4. in case the policy forbids your overloads, discuss
private
fun kotlinExtensionFunctionsFor(type: ApiType, sinceSupplier: (String) -> String?): Sequence<KotlinExtensionFunction> =
    candidatesForExtensionFrom(type)
        .sortedWithTypeOfTakingFunctionsFirst()
        .flatMap { function ->

            val candidateFor = object {

                val groovyNamedArgumentsToVarargs =
                    function.parameters.firstOrNull()?.type?.isGroovyNamedArgumentMap == true

                val javaClassToKotlinClass =
                    function.parameters.any { it.type.hasJavaClass() }

                val extension
                    get() = groovyNamedArgumentsToVarargs || javaClassToKotlinClass
            }

            if (!candidateFor.extension) {
                return@flatMap emptySequence<KotlinExtensionFunction>()
            }

            val extensionTypeParameters = function.typeParameters + type.typeParameters

            sequenceOf(
                KotlinExtensionFunction(
                    description = "Kotlin extension function ${if (candidateFor.javaClassToKotlinClass) "taking [kotlin.reflect.KClass] " else ""}for [${type.sourceName}.${function.name}]",
                    since = sinceSupplier(function.binarySignature),
                    isIncubating = function.isIncubating,
                    isDeprecated = function.isDeprecated,
                    typeParameters = extensionTypeParameters,
                    targetType = type,
                    name = function.name,
                    parameters = function.newMappedParameters().groovyNamedArgumentsToVarargs().javaClassToKotlinClass(),
                    returnType = function.returnType
                )
            )
        }


private
fun ApiTypeUsage.hasJavaClass(): Boolean =
    isJavaClass ||
        isKotlinArray && typeArguments.single().isJavaClass ||
        isKotlinCollection && typeArguments.single().isJavaClass


private
fun candidatesForExtensionFrom(type: ApiType): Sequence<ApiFunction> =
    type.functions.filter(::isCandidateForExtension).asSequence()


private
fun Sequence<ApiFunction>.sortedWithTypeOfTakingFunctionsFirst() =
    sortedBy { f ->
        if (f.parameters.any { it.type.isGradleTypeOf }) 0
        else 1
    }


private
fun ApiFunction.newMappedParameters() =
    parameters.map { MappedApiFunctionParameter(it) }


private
data class MappedApiFunctionParameter(
    val original: ApiFunctionParameter,
    val index: Int = original.index,
    val type: ApiTypeUsage = original.type,
    val isVarargs: Boolean = original.isVarargs,
    val asArgument: String = "${if (original.isVarargs) "*" else ""}`${original.name ?: "p$index"}`"
) {
    val name: String
        get() = original.name ?: "p$index"
}


private
fun List<MappedApiFunctionParameter>.groovyNamedArgumentsToVarargs() =
    firstOrNull()?.takeIf { it.type.isGroovyNamedArgumentMap }?.let { first ->
        val mappedMapParameter = first.copy(
            type = ApiTypeUsage(
                sourceName = SourceNames.kotlinArray,
                typeArguments = listOf(
                    ApiTypeUsage(
                        "Pair",
                        typeArguments = listOf(
                            ApiTypeUsage("String"),
                            ApiTypeUsage("Any", isNullable = false)
                        )
                    )
                )
            ),
            isVarargs = true,
            asArgument = "mapOf(*${first.asArgument})"
        )
        if (last().type.isSAM) last().let { action -> drop(1).dropLast(1) + mappedMapParameter + action }
        else drop(1) + mappedMapParameter
    } ?: this


private
fun List<MappedApiFunctionParameter>.javaClassToKotlinClass() =
    map { p ->
        p.type.run {
            when {
                isJavaClass -> p.copy(
                    type = toKotlinClass(),
                    asArgument = "${p.asArgument}.java"
                )

                isKotlinArray && typeArguments.single().isJavaClass -> p.copy(
                    type = toArrayOfKotlinClasses(),
                    asArgument = "${p.asArgument}.map { it.java }.toTypedArray()"
                )

                isKotlinCollection && typeArguments.single().isJavaClass -> p.copy(
                    type = toCollectionOfKotlinClasses(),
                    asArgument = "${p.asArgument}.map { it.java }"
                )

                else -> p
            }
        }
    }


private
data class KotlinExtensionFunction(
    val description: String,
    val since: String?,
    val isIncubating: Boolean,
    val isDeprecated: Boolean,
    val typeParameters: List<ApiTypeUsage>,
    val targetType: ApiType,
    val name: String,
    val parameters: List<MappedApiFunctionParameter>,
    val returnType: ApiTypeUsage
) {

    fun toKotlinString(): String = StringBuilder().apply {

        appendReproducibleNewLine(
            """
               |/**
               | * $description.
               | *
               | * @see ${targetType.sourceName}.$name
            ${"| * @since ".appendOrNull(since) ?: ""}
               | */
            """.trimMargin().dropBlankLines()
        )
        if (isDeprecated) appendReproducibleNewLine("""@Deprecated("Deprecated Gradle API")""")
        if (isIncubating) appendReproducibleNewLine("@org.gradle.api.Incubating")
        append("inline fun ")
        if (typeParameters.isNotEmpty()) append("${typeParameters.joinInAngleBrackets { it.toTypeParameterString() }} ")
        append(targetType.sourceName)
        if (targetType.typeParameters.isNotEmpty()) append(targetType.typeParameters.toTypeArgumentsString(targetType))
        append(".")
        append("`$name`")
        append("(")
        append(parameters.toDeclarationString())
        append("): ")
        append(returnType.toTypeArgumentString())
        appendReproducibleNewLine(" =")
        appendReproducibleNewLine("`$name`(${parameters.toArgumentsString()})".prependIndent())
        appendReproducibleNewLine()
    }.toString()

    private
    fun String.appendOrNull(s: String?) =
        if (s == null) null else "$this$s"

    private
    fun String.dropBlankLines(): String =
        lineSequence().filter(String::isNotBlank).joinToString("\n")

    private
    fun List<MappedApiFunctionParameter>.toDeclarationString(): String =
        takeIf { it.isNotEmpty() }?.let { list ->
            list.mapIndexed { index, p ->
                when {
                    index == list.lastIndex && p.isVarargs && p.type.isKotlinArray -> "vararg `${p.name}`: ${singleTypeArgumentStringOf(p)}"
                    index == list.size - 2 && list.last().type.isSAM && p.isVarargs && p.type.isKotlinArray -> "vararg `${p.name}`: ${singleTypeArgumentStringOf(p)}"
                    else -> "`${p.name}`: ${p.type.toTypeArgumentString()}"
                }
            }.joinToString(separator = ", ")
        } ?: ""

    private
    fun singleTypeArgumentStringOf(p: MappedApiFunctionParameter) =
        p.type.typeArguments.single().toTypeArgumentString()

    private
    fun List<MappedApiFunctionParameter>.toArgumentsString(): String =
        takeIf { it.isNotEmpty() }
            ?.sortedBy { it.original.index }
            ?.joinToString(separator = ", ") { it.asArgument }
            ?: ""
}


private
fun ApiTypeUsage.toKotlinClass() =
    ApiTypeUsage(
        SourceNames.kotlinClass,
        isNullable,
        typeArguments = singleTypeArgumentRawToStarProjection()
    )


private
fun ApiTypeUsage.toArrayOfKotlinClasses() =
    ApiTypeUsage(
        SourceNames.kotlinArray,
        isNullable,
        typeArguments = listOf(ApiTypeUsage(SourceNames.kotlinClass, typeArguments = typeArguments.single().singleTypeArgumentRawToStarProjection()))
    )


private
fun ApiTypeUsage.toCollectionOfKotlinClasses() =
    ApiTypeUsage(
        SourceNames.kotlinCollection,
        isNullable,
        typeArguments = listOf(ApiTypeUsage(SourceNames.kotlinClass, typeArguments = typeArguments.single().singleTypeArgumentRawToStarProjection()))
    )


private
fun ApiTypeUsage.singleTypeArgumentRawToStarProjection(): List<ApiTypeUsage> =
    if (isRaw) singletonListOfStarProjectionTypeUsage
    else typeArguments.also { it.single() }


private
fun Boolean.toKotlinNullabilityString(): String =
    if (this) "?" else ""


private
fun ApiTypeUsage.toTypeParameterString(): String =
    "$sourceName${bounds.toBoundsString()}${typeArguments.toTypeParametersString(type)}${isNullable.toKotlinNullabilityString()}"


private
fun List<ApiTypeUsage>.toBoundsString() =
    takeIf { it.isNotEmpty() }?.let { " : ${it.single().toTypeParameterString()}" } ?: ""


private
fun List<ApiTypeUsage>.toTypeParametersString(type: ApiType? = null): String =
    rawTypesToStarProjections(type).joinInAngleBrackets { it.toTypeParameterString() }


private
fun ApiTypeUsage.toTypeArgumentString(): String =
    "${variance.toKotlinString()}$sourceName${typeArguments.toTypeArgumentsString(type)}${isNullable.toKotlinNullabilityString()}"


private
fun Variance.toKotlinString() =
    when (this) {
        Variance.INVARIANT -> ""
        Variance.COVARIANT -> "out "
        Variance.CONTRAVARIANT -> "in "
    }


private
fun List<ApiTypeUsage>.toTypeArgumentsString(type: ApiType? = null): String =
    rawTypesToStarProjections(type).joinInAngleBrackets { it.toTypeArgumentString() }


private
fun List<ApiTypeUsage>.rawTypesToStarProjections(type: ApiType? = null): List<ApiTypeUsage> =
    when {
        isNotEmpty() -> this
        type?.typeParameters?.isNotEmpty() == true -> List(type.typeParameters.size) { starProjectionTypeUsage }
        else -> emptyList()
    }


private
fun <T> List<T>?.joinInAngleBrackets(transform: (T) -> CharSequence = { it.toString() }) =
    this?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = "<", postfix = ">", transform = transform)
        ?: ""


private
val ApiTypeUsage.isGroovyNamedArgumentMap
    get() = isMap && (
        typeArguments.all { it.isAny }
            || typeArguments.all { it.isStarProjectionTypeUsage }
            || (typeArguments[0].isString && (typeArguments[1].isStarProjectionTypeUsage || typeArguments[1].isAny))
        )


private
object SourceNames {
    const val javaClass = "java.lang.Class"
    const val groovyClosure = "groovy.lang.Closure"
    const val gradleAction = "org.gradle.api.Action"
    const val gradleTypeOf = "org.gradle.api.reflect.TypeOf"
    const val kotlinClass = "kotlin.reflect.KClass"
    const val kotlinArray = "kotlin.Array"
    const val kotlinCollection = "kotlin.collections.Collection"
}


private
val ApiTypeUsage.isSAM
    get() = type?.isSAM == true


private
val ApiTypeUsage.isAny
    get() = sourceName == "Any"


private
val ApiTypeUsage.isString
    get() = sourceName == "String"


private
val ApiTypeUsage.isMap
    get() = sourceName == "kotlin.collections.Map"


private
val ApiTypeUsage.isJavaClass
    get() = sourceName == SourceNames.javaClass


private
val ApiTypeUsage.isGroovyClosure
    get() = sourceName == SourceNames.groovyClosure


private
val ApiTypeUsage.isGradleTypeOf
    get() = sourceName == SourceNames.gradleTypeOf


private
val ApiTypeUsage.isKotlinArray
    get() = sourceName == SourceNames.kotlinArray


private
val ApiTypeUsage.isKotlinCollection
    get() = sourceName == SourceNames.kotlinCollection


private
fun isCandidateForExtension(function: ApiFunction): Boolean = function.run {
    name !in functionNameBlackList &&
        isPublic &&
        !isStatic &&
        parameters.none { it.type.isGroovyClosure }
}


private
val functionNameBlackList = listOf("<init>")
