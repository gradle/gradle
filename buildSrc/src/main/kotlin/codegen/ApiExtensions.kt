package codegen

import org.gradle.api.Action
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType

fun codeForExtensionsOf(type: KClass<*>): String =
    "package ${type.packageName}\n\n" +
        extensibleFunctionsOf(type).joinToString(separator = "\n\n") {
            extensionCodeFor(it)
        }

fun extensibleFunctionsOf(type: KClass<*>): List<ExtensibleFunction> =
    type.declaredMemberFunctions.filter(::isExtensible).map(::ExtensibleFunction)

fun isExtensible(fn: KFunction<*>) =
    !fn.isGeneric() && fn.parameters.isNotEmpty() && isExtensibleParameterType(fn.parameters.last().type.javaType)

data class ExtensibleFunction(val function: KFunction<*>) {
    val receiver: Type = function.parameters[0].type.javaType
    val parameters: List<KParameter> = function.parameters.subList(1, function.parameters.size)
    val name: String
        get() = function.name
}

fun extensionCodeFor(fn: ExtensibleFunction): String =
    "${extensionDeclarationFor(fn)} =\n    ${extensionBodyFor(fn)}"

fun extensionDeclarationFor(fn: ExtensibleFunction): String =
    "inline fun ${fn.receiver.simpleName}.${fn.name}(${fn.parameters.joinToString { parameterStringFor(it) }})"

fun extensionBodyFor(fn: ExtensibleFunction): String =
    "this.${fn.name}(${fn.parameters.joinToString { argumentStringFor(it) }})"

fun parameterStringFor(parameter: KParameter): String {
    val type = parameter.type.javaType
    if (type is ParameterizedType) {
        when (type.rawType) {
            Action::class.java ->
                return "crossinline ${parameter.name}: ${typeStringFor(type.actualTypeArguments[0])}.() -> Unit"
        }
    }
    return "${parameter.name}: ${typeStringFor(type)}"
}

fun typeStringFor(type: Type): String =
    when (type) {
        is WildcardType -> typeStringFor(type.lowerBounds.first())
        is Class<*> ->
            when (type) {
                String::class.java -> "String"
                else -> type.name
            }
        else -> throw NotImplementedError("typeStringFor(${type.javaClass}) => $type")
    }

fun argumentStringFor(parameter: KParameter): String =
    if (isExtensibleParameterType(parameter.type.javaType)) {
        "${parameter.type.qualifiedName} { ${parameter.name!!}(it) }"
    } else {
        parameter.name!!
    }

fun <T> KFunction<T>.isGeneric() =
    this.javaMethod!!.typeParameters.isNotEmpty()

fun isExtensibleParameterType(type: Type) =
    when (type) {
        is ParameterizedType -> type.rawType == Action::class.java
        else -> false
    }

val <T : Any> KClass<T>.packageName: String
    get() = this.java.`package`.name

val KType.qualifiedName: String
    get() = this.javaType.qualifiedName

val Type.qualifiedName: String
    get() = when (this) {
        is ParameterizedType -> this.rawType.qualifiedName
        is Class<*> -> this.name
        else -> throw NotImplementedError("${this.javaClass}")
    }

val KType.simpleName: String
    get() = this.javaType.simpleName

val Type.simpleName: String
    get() = when (this) {
        is ParameterizedType -> this.rawType.simpleName
        is Class<*> -> this.simpleName
        else -> throw NotImplementedError("${this.javaClass}")
    }
