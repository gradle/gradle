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

import org.gradle.kotlin.dsl.internal.sharedruntime.support.ClassBytesRepository
import org.gradle.kotlin.dsl.internal.sharedruntime.support.unsafeLazy
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Attribute
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ACC_VARARGS
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.Closeable
import java.io.File
import java.util.ArrayDeque
import javax.annotation.Nullable


fun apiTypeProviderFor(
    asmLevel: Int,
    platformClassLoader: ClassLoader,
    incubatingAnnotationTypeDescriptor: String,
    classPath: List<File>,
    classPathDependencies: List<File> = emptyList(),
    parameterNamesSupplier: ParameterNamesSupplier = { null },
): ApiTypeProvider =

    ApiTypeProvider(
        asmLevel,
        incubatingAnnotationTypeDescriptor,
        ClassBytesRepository(
            platformClassLoader,
            classPath,
            classPathDependencies,
        ),
        parameterNamesSupplier
    )


private
typealias ApiTypeSupplier = () -> ApiType


typealias ParameterNamesSupplier = (String) -> List<String>?


private
fun ParameterNamesSupplier.parameterNamesFor(typeName: String, functionName: String, parameterTypeNames: List<String>): List<String>? =
    this("$typeName.$functionName(${parameterTypeNames.joinToString(",")})")


/**
 * Provides [ApiType] instances by Kotlin source name from a class path.
 *
 * Keeps JAR files open for fast lookup, must be closed.
 * Once closed, type graph navigation from [ApiType] and [ApiFunction] instances
 * will throw [IllegalStateException].
 *
 * Limitations:
 * - supports Java byte code only, not Kotlin
 * - does not support nested Java arrays as method parameters
 * - does not support generics with multiple bounds
 */
class ApiTypeProvider internal constructor(
    private val asmLevel: Int,
    private val incubatingAnnotationTypeDescriptor: String,
    private val repository: ClassBytesRepository,
    parameterNamesSupplier: ParameterNamesSupplier
) : Closeable {

    private
    val context = Context(this, parameterNamesSupplier)

    private
    val apiTypesBySourceName = mutableMapOf<String, ApiTypeSupplier?>()

    private
    var closed = false

    fun type(sourceName: String): ApiType? = open {
        apiTypesBySourceName.computeIfAbsent(sourceName) {
            repository.classBytesFor(sourceName)?.let { apiTypeFor(sourceName) { it } }
        }?.invoke()
    }

    internal
    fun allTypes(): Sequence<ApiType> = open {
        repository.allClassesBytesBySourceName().map { (sourceName, classBytes) ->
            apiTypesBySourceName.computeIfAbsent(sourceName) {
                apiTypeFor(sourceName, classBytes)
            }!!
        }.map { it() }
    }

    override fun close() =
        try {
            repository.close()
        } finally {
            closed = true
        }

    private
    fun apiTypeFor(sourceName: String, classBytes: () -> ByteArray): () -> ApiType = {
        ApiType(asmLevel, incubatingAnnotationTypeDescriptor, sourceName, classNodeFor(classBytes), context)
    }

    private
    fun classNodeFor(classBytesSupplier: () -> ByteArray): () -> ApiTypeClassNode = {
        ApiTypeClassNode(asmLevel).also {
            ClassReader(classBytesSupplier()).accept(it, SKIP_CODE or SKIP_FRAMES)
        }
    }

    private
    fun <T> open(action: () -> T): T =
        if (closed) throw IllegalStateException("ApiTypeProvider closed!")
        else action()

    internal
    class Context(
        private val typeProvider: ApiTypeProvider,
        private val parameterNamesSupplier: ParameterNamesSupplier
    ) {
        fun type(sourceName: String): ApiType? =
            typeProvider.type(sourceName)

        fun parameterNamesFor(typeName: String, functionName: String, parameterTypeNames: List<String>): List<String>? =
            parameterNamesSupplier.parameterNamesFor(typeName, functionName, parameterTypeNames)
    }
}


class ApiType internal constructor(
    private val asmLevel: Int,
    private val incubatingAnnotationTypeDescriptor: String,
    val sourceName: String,
    private val delegateSupplier: () -> ClassNode,
    private val context: ApiTypeProvider.Context
) {

    internal
    val binaryName: String
        get() = binaryNameOfInternalName(delegate.name)

    val isPublic: Boolean
        get() = delegate.access.isPublic

    internal
    val isDeprecated: Boolean
        get() = delegate.visibleAnnotations.has<java.lang.Deprecated>()

    internal
    val isIncubating: Boolean
        get() = delegate.visibleAnnotations.has(incubatingAnnotationTypeDescriptor)

    val isSAM: Boolean by unsafeLazy {
        delegate.access.isAbstract && singleAbstractMethodOf(delegate)?.access?.isPublic == true
    }

    val typeParameters: List<ApiTypeUsage> by unsafeLazy {
        context.apiTypeParametersFor(visitedSignature)
    }

    val functions: List<ApiFunction> by unsafeLazy {
        delegate.methods.filter(::isSignificantDeclaration).map { ApiFunction(asmLevel, incubatingAnnotationTypeDescriptor, this, it, context) }
    }

    private
    fun singleAbstractMethodOf(classNode: ClassNode) =
        classNode.methods.singleOrNull { it.access.run { !isStatic && isAbstract } }

    /**
     * Test if a method is a prime declaration or an overrides that change the signature.
     *
     * There's no way to tell from the byte code that a method overrides the signature
     * of a parent declaration other than crawling up the type hierarchy.
     */
    private
    fun isSignificantDeclaration(methodNode: MethodNode): Boolean {

        if (methodNode.access.isSynthetic) return false

        if (!hasSuperType) return true

        fun ArrayDeque<String>.addSuperTypesOf(classNode: ClassNode) {
            classNode.interfaces.forEach { push(it) }
            if (classNode.superName != null) push(classNode.superName)
        }

        val superTypeStack = ArrayDeque<String>().apply {
            addSuperTypesOf(delegate)
        }

        val visited = mutableSetOf<String>()

        val matchesMethodNode = { candidate: MethodNode ->
            candidate.desc == methodNode.desc && candidate.signature == methodNode.signature
        }

        while (superTypeStack.isNotEmpty()) {
            val superTypeName = superTypeStack.pop()

            if (!visited.add(superTypeName)) continue

            val superType = typeForInternalName(superTypeName) ?: continue

            if (superType.delegate.methods.any(matchesMethodNode)) return false

            superTypeStack.addSuperTypesOf(superType.delegate)
        }
        return true
    }

    private
    fun typeForInternalName(internalType: String): ApiType? =
        context.type(sourceNameOfBinaryName(binaryNameOfInternalName(internalType)))

    private
    val hasSuperType: Boolean by unsafeLazy {
        delegate.interfaces.isNotEmpty() || delegate.superName != null
    }

    private
    val delegate: ClassNode by unsafeLazy {
        delegateSupplier()
    }

    private
    val visitedSignature: ClassSignatureVisitor? by unsafeLazy {
        delegate.signature?.let { signature ->
            ClassSignatureVisitor(asmLevel).also { SignatureReader(signature).accept(it) }
        }
    }
}


class ApiFunction internal constructor(
    private val asmLevel: Int,
    private val incubatingAnnotationTypeDescriptor: String,
    internal val owner: ApiType,
    private val delegate: MethodNode,
    private val context: ApiTypeProvider.Context
) {
    val name: String =
        delegate.name

    internal
    val isPublic: Boolean =
        delegate.access.isPublic

    internal
    val isDeprecated: Boolean
        get() = owner.isDeprecated || delegate.visibleAnnotations.has<java.lang.Deprecated>()

    internal
    val isIncubating: Boolean
        get() = owner.isIncubating || delegate.visibleAnnotations.has(incubatingAnnotationTypeDescriptor)

    internal
    val isStatic: Boolean =
        delegate.access.isStatic

    val typeParameters: List<ApiTypeUsage> by unsafeLazy {
        context.apiTypeParametersFor(visitedSignature)
    }

    val parameters: List<ApiFunctionParameter> by unsafeLazy {
        context.apiFunctionParametersFor(this, delegate, visitedSignature)
    }

    val returnType: ApiTypeUsage by unsafeLazy {
        context.apiTypeUsageForReturnType(delegate, visitedSignature?.returnType)
    }

    internal
    val binarySignature: String
        get() = "${owner.binaryName}.$name(${parameters.joinToString(",") { it.typeBinaryName }})"

    private
    val visitedSignature: MethodSignatureVisitor? by unsafeLazy {
        delegate.signature?.let { signature ->
            MethodSignatureVisitor(asmLevel).also { visitor -> SignatureReader(signature).accept(visitor) }
        }
    }
}


data class ApiTypeUsage internal constructor(
    val sourceName: String,
    internal val isNullable: Boolean = false,
    val type: ApiType? = null,
    val variance: Variance = Variance.INVARIANT,
    val typeArguments: List<ApiTypeUsage> = emptyList(),
    val bounds: List<ApiTypeUsage> = emptyList()
) {
    /**
     * Type usage is raw if type has no type parameters or if usage has no type arguments.
     */
    internal
    val isRaw: Boolean
        get() = type?.typeParameters?.isEmpty() != false || typeArguments.isEmpty()
}


enum class Variance {

    /**
     * Represent an invariant type argument.
     * e.g. `<T>`
     */
    INVARIANT,

    /**
     * Represent a covariant type argument.
     * Also known as "extends-bound" or "upper bound".
     * e.g. `<? extends T>`
     */
    COVARIANT,

    /**
     * Represent a contravariant type argument.
     * Also known as "super-bound" or "lower bound".
     * e.g. `<? super T>`
     */
    CONTRAVARIANT
}


data class ApiFunctionParameter internal constructor(
    internal val index: Int,
    internal val isVarargs: Boolean,
    private val nameSupplier: () -> String?,
    internal val typeBinaryName: String,
    val type: ApiTypeUsage
) {

    internal
    val name: String? by unsafeLazy {
        nameSupplier()
    }
}


private
fun ApiTypeProvider.Context.apiTypeUsageFor(
    binaryName: String,
    isNullable: Boolean = false,
    variance: Variance = Variance.INVARIANT,
    typeArguments: List<TypeSignatureVisitor> = emptyList(),
    bounds: List<TypeSignatureVisitor> = emptyList()
): ApiTypeUsage =

    if (binaryName == "?") starProjectionTypeUsage
    else sourceNameOfBinaryName(binaryName).let { sourceName ->
        ApiTypeUsage(
            sourceName,
            isNullable,
            type(sourceName),
            variance,
            typeArguments.map { apiTypeUsageFor(it.binaryName, variance = it.variance, typeArguments = it.typeArguments) },
            bounds.map { apiTypeUsageFor(it.binaryName, variance = it.variance, typeArguments = it.typeArguments) }
        )
    }


internal
val ApiTypeUsage.isStarProjectionTypeUsage
    get() = this === starProjectionTypeUsage


internal
val starProjectionTypeUsage = ApiTypeUsage("*")


internal
val singletonListOfStarProjectionTypeUsage = listOf(starProjectionTypeUsage)


private
fun ApiTypeProvider.Context.apiTypeParametersFor(visitedSignature: BaseSignatureVisitor?): List<ApiTypeUsage> =
    visitedSignature?.typeParameters?.map { (binaryName, bounds) -> apiTypeUsageFor(binaryName, bounds = bounds) }
        ?: emptyList()


private
fun ApiTypeProvider.Context.apiFunctionParametersFor(function: ApiFunction, delegate: MethodNode, visitedSignature: MethodSignatureVisitor?) =
    delegate.visibleParameterAnnotations?.map { it.has<Nullable>() }.let { parametersNullability ->
        val parameterTypesBinaryNames = visitedSignature?.parameters?.map { if (it.isArray) "${it.typeArguments.single().binaryName}[]" else it.binaryName }
            ?: Type.getArgumentTypes(delegate.desc).map { it.className }
        val names by unsafeLazy {
            parameterNamesFor(
                function.owner.sourceName,
                function.name,
                parameterTypesBinaryNames
            )
        }
        parameterTypesBinaryNames.mapIndexed { idx, parameterTypeBinaryName ->
            val isNullable = parametersNullability?.get(idx) == true
            val signatureParameter = visitedSignature?.parameters?.get(idx)
            val parameterTypeName = signatureParameter?.binaryName ?: parameterTypeBinaryName
            val variance = signatureParameter?.variance ?: Variance.INVARIANT
            val typeArguments = signatureParameter?.typeArguments ?: emptyList()
            ApiFunctionParameter(
                index = idx,
                isVarargs = idx == parameterTypesBinaryNames.lastIndex && delegate.access.isVarargs,
                nameSupplier = {
                    names?.get(idx) ?: delegate.parameters?.get(idx)?.name
                },
                typeBinaryName = parameterTypeBinaryName,
                type = apiTypeUsageFor(parameterTypeName, isNullable, variance, typeArguments)
            )
        }
    }


private
fun ApiTypeProvider.Context.apiTypeUsageForReturnType(delegate: MethodNode, returnType: TypeSignatureVisitor?) =
    apiTypeUsageFor(
        returnType?.binaryName ?: Type.getReturnType(delegate.desc).className,
        delegate.visibleAnnotations.has<Nullable>(),
        returnType?.variance ?: Variance.INVARIANT,
        returnType?.typeArguments ?: emptyList()
    )


private
inline fun <reified AnnotationType : Any> List<AnnotationNode>?.has() =
    has(Type.getDescriptor(AnnotationType::class.java))


private
fun List<AnnotationNode>?.has(annotationTypeDescriptor: String) =
    this?.any { it.desc == annotationTypeDescriptor } ?: false


private
class ApiTypeClassNode(asmLevel: Int) : ClassNode(asmLevel) {

    override fun visitSource(file: String?, debug: String?) = Unit
    override fun visitOuterClass(owner: String?, name: String?, desc: String?) = Unit
    override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? = null
    override fun visitAttribute(attr: Attribute?) = Unit
    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) = Unit
    override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? = null
}


private
abstract class BaseSignatureVisitor(private val asmLevel: Int) : SignatureVisitor(asmLevel) {

    val typeParameters: MutableMap<String, MutableList<TypeSignatureVisitor>> = LinkedHashMap(1)

    private
    var currentTypeParameter: String? = null

    override fun visitFormalTypeParameter(binaryName: String) {
        typeParameters[binaryName] = ArrayList(1)
        currentTypeParameter = binaryName
    }

    override fun visitClassBound(): SignatureVisitor =
        visitTypeParameterBound()

    override fun visitInterfaceBound(): SignatureVisitor =
        visitTypeParameterBound()

    private
    fun visitTypeParameterBound() =
        TypeSignatureVisitor(asmLevel).also { typeParameters[currentTypeParameter]!!.add(it) }
}


private
class ClassSignatureVisitor(asmLevel: Int) : BaseSignatureVisitor(asmLevel)


private
class MethodSignatureVisitor(private val asmLevel: Int) : BaseSignatureVisitor(asmLevel) {

    val parameters: MutableList<TypeSignatureVisitor> = ArrayList(1)

    val returnType = TypeSignatureVisitor(asmLevel)

    override fun visitParameterType(): SignatureVisitor =
        TypeSignatureVisitor(asmLevel).also { parameters.add(it) }

    override fun visitReturnType(): SignatureVisitor =
        returnType
}


private
class TypeSignatureVisitor(private val asmLevel: Int, val variance: Variance = Variance.INVARIANT) : SignatureVisitor(asmLevel) {

    var isArray = false

    lateinit var binaryName: String

    val typeArguments = ArrayList<TypeSignatureVisitor>(1)

    private
    var expectTypeArgument = false

    override fun visitBaseType(descriptor: Char) =
        visitBinaryName(binaryNameOfBaseType(descriptor))

    override fun visitArrayType(): SignatureVisitor =
        TypeSignatureVisitor(asmLevel).also {
            visitBinaryName("kotlin.Array")
            isArray = true
            typeArguments.add(it)
        }

    override fun visitClassType(internalName: String) =
        visitBinaryName(binaryNameOfInternalName(internalName))

    override fun visitInnerClassType(localName: String) {
        binaryName += "${'$'}$localName"
    }

    override fun visitTypeArgument() {
        typeArguments.add(TypeSignatureVisitor(asmLevel).also { it.binaryName = "?" })
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor =
        TypeSignatureVisitor(asmLevel, boundOf(wildcard)).also {
            expectTypeArgument = true
            typeArguments.add(it)
        }

    override fun visitTypeVariable(internalName: String) {
        visitBinaryName(binaryNameOfInternalName(internalName))
    }

    private
    fun visitBinaryName(binaryName: String) {
        if (expectTypeArgument) {
            TypeSignatureVisitor(asmLevel).let {
                typeArguments.add(it)
                SignatureReader(binaryName).accept(it)
            }
            expectTypeArgument = false
        } else {
            this.binaryName = binaryName
        }
    }

    private
    fun boundOf(wildcard: Char) =
        when (wildcard) {
            '+' -> Variance.COVARIANT
            '-' -> Variance.CONTRAVARIANT
            else -> Variance.INVARIANT
        }
}


private
fun binaryNameOfBaseType(descriptor: Char) =
    Type.getType(descriptor.toString()).className


private
fun binaryNameOfInternalName(internalName: String): String =
    Type.getObjectType(internalName).className


fun sourceNameOfBinaryName(binaryName: String): String =
    when (binaryName) {
        "void" -> "Unit"
        "?" -> "*"
        in mappedTypeStrings.keys -> mappedTypeStrings[binaryName]!!
        in primitiveTypeStrings.keys -> primitiveTypeStrings[binaryName]!!
        else -> binaryName.replace('$', '.')
    }


/**
 * See https://kotlinlang.org/docs/reference/java-interop.html#mapped-types
 */
private
val mappedTypeStrings =
    mapOf(
        // Built-ins
        "java.lang.Cloneable" to "kotlin.Cloneable",
        "java.lang.Comparable" to "kotlin.Comparable",
        "java.lang.Enum" to "kotlin.Enum",
        "java.lang.annotation.Annotation" to "kotlin.Annotation",
        "java.lang.Deprecated" to "kotlin.Deprecated",
        "java.lang.CharSequence" to "kotlin.CharSequence",
        "java.lang.Number" to "kotlin.Number",
        "java.lang.Throwable" to "kotlin.Throwable",
        // Collections
        "java.util.Iterable" to "kotlin.collections.Iterable",
        "java.util.Iterator" to "kotlin.collections.Iterator",
        "java.util.ListIterator" to "kotlin.collections.ListIterator",
        "java.util.Collection" to "kotlin.collections.Collection",
        "java.util.List" to "kotlin.collections.List",
        "java.util.ArrayList" to "kotlin.collections.ArrayList",
        "java.util.Set" to "kotlin.collections.Set",
        "java.util.HashSet" to "kotlin.collections.HashSet",
        "java.util.LinkedHashSet" to "kotlin.collections.LinkedHashSet",
        "java.util.Map" to "kotlin.collections.Map",
        "java.util.Map.Entry" to "kotlin.collections.Map.Entry",
        "java.util.HashMap" to "kotlin.collections.HashMap",
        "java.util.LinkedHashMap" to "kotlin.collections.LinkedHashMap"
    )


private
inline val Int.isStatic: Boolean
    get() = ACC_STATIC in this


private
inline val Int.isPublic: Boolean
    get() = ACC_PUBLIC in this


private
inline val Int.isAbstract: Boolean
    get() = ACC_ABSTRACT in this


private
inline val Int.isVarargs: Boolean
    get() = ACC_VARARGS in this


private
inline val Int.isSynthetic: Boolean
    get() = ACC_SYNTHETIC in this


private
operator fun Int.contains(flag: Int) =
    and(flag) == flag
