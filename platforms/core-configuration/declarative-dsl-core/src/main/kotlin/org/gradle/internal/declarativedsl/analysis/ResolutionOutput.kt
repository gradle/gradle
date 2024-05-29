package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.PropertyAccess


// TODO: report failures to resolve with potential candidates that could not work
data class PropertyReferenceResolution(
    val receiverObject: ObjectOrigin,
    val property: DataProperty
) {
    override fun toString(): String = "$receiverObject${'.'}${property.name}"
}


data class AssignmentRecord(
    val lhs: PropertyReferenceResolution,
    val rhs: ObjectOrigin,
    val operationId: OperationId,
    val assignmentMethod: AssignmentMethod,
    val originElement: LanguageTreeElement
)


sealed interface AssignmentMethod {
    data object Property : AssignmentMethod
    data object AsConstructed : AssignmentMethod
    data class BuilderFunction(val function: DataBuilderFunction) : AssignmentMethod
}


sealed interface ObjectOrigin {
    val originElement: LanguageTreeElement

    sealed interface HasReceiver : ObjectOrigin {
        val receiver: ObjectOrigin
    }

    sealed interface DelegatingObjectOrigin : ObjectOrigin {
        val delegate: ObjectOrigin
    }

    data class ImplicitThisReceiver(
        val resolvedTo: ReceiverOrigin,
        val isCurrentScopeReceiver: Boolean
    ) : ObjectOrigin, DelegatingObjectOrigin {
        override val delegate: ObjectOrigin
            get() = resolvedTo

        override val originElement: LanguageTreeElement
            get() = resolvedTo.originElement

        override fun toString(): String = "(this:$resolvedTo)"
    }

    sealed interface ReceiverOrigin : ObjectOrigin

    data class TopLevelReceiver(val type: DataType, override val originElement: LanguageTreeElement) : ReceiverOrigin {
        override fun toString(): String = "(top-level-object)"
    }

    data class FromLocalValue(val localValue: LocalValue, val assigned: ObjectOrigin) : DelegatingObjectOrigin {
        override val delegate: ObjectOrigin
            get() = assigned

        override val originElement: LanguageTreeElement
            get() = assigned.originElement

        override fun toString(): String = "(val ${localValue.name} = $assigned)"
    }

    data class ConstantOrigin(val literal: Literal<*>) : ObjectOrigin {
        override val originElement: LanguageTreeElement
            get() = literal

        override fun toString(): String = "${literal.value.let { if (it is String) "\"$it\"" else it }}"
    }

    data class NullObjectOrigin(override val originElement: Null) : ObjectOrigin

    sealed interface FunctionOrigin : ObjectOrigin {
        val function: SchemaFunction
        val invocationId: OperationId
        val receiver: ObjectOrigin?
    }

    sealed interface FunctionInvocationOrigin : FunctionOrigin {
        val parameterBindings: ParameterValueBinding
    }

    data class BuilderReturnedReceiver(
        override val function: SchemaFunction,
        override val receiver: ObjectOrigin,
        override val originElement: FunctionCall,
        override val parameterBindings: ParameterValueBinding,
        override val invocationId: OperationId
    ) : FunctionInvocationOrigin, DelegatingObjectOrigin, HasReceiver {
        override fun toString(): String = receiver.toString()

        override val delegate: ObjectOrigin
            get() = receiver
    }

    data class NewObjectFromMemberFunction(
        override val function: SchemaMemberFunction,
        override val receiver: ObjectOrigin,
        override val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        override val invocationId: OperationId
    ) : FunctionInvocationOrigin, HasReceiver {
        override fun toString(): String =
            functionInvocationString(function, receiver, invocationId, parameterBindings)
    }

    data class NewObjectFromTopLevelFunction(
        override val function: SchemaFunction,
        override val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        override val invocationId: OperationId
    ) : FunctionInvocationOrigin {
        override val receiver: ObjectOrigin?
            get() = null

        override fun toString(): String = functionInvocationString(function, null, invocationId, parameterBindings)
    }

    data class AccessAndConfigureReceiver(
        override val receiver: ObjectOrigin,
        override val function: SchemaFunction,
        override val originElement: FunctionCall,
        override val parameterBindings: ParameterValueBinding,
        override val invocationId: OperationId,
        val accessor: ConfigureAccessor,
    ) : FunctionInvocationOrigin, ReceiverOrigin, DelegatingObjectOrigin {
        override fun toString(): String = accessor.access(receiver, this).toString()

        override val delegate: ObjectOrigin
            get() = accessor.access(receiver, this)
    }

    data class AddAndConfigureReceiver(
        override val receiver: FunctionOrigin,
    ) : FunctionOrigin, DelegatingObjectOrigin, ReceiverOrigin {
        override val invocationId: OperationId
            get() = receiver.invocationId
        override val originElement: LanguageTreeElement
            get() = receiver.originElement
        override val function: SchemaFunction
            get() = receiver.function
        override fun toString(): String = receiver.toString()

        override val delegate: ObjectOrigin
            get() = receiver
    }

    data class PropertyReference(
        override val receiver: ObjectOrigin,
        val property: DataProperty,
        override val originElement: LanguageTreeElement
    ) : ObjectOrigin, HasReceiver {
        override fun toString(): String = "$receiver${'.'}${property.name}"
    }

    data class CustomConfigureAccessor(
        override val receiver: ObjectOrigin,
        val accessor: ConfigureAccessor.Custom,
        override val originElement: LanguageTreeElement
    ) : ObjectOrigin, HasReceiver {
        override fun toString(): String = "$receiver${'.'}${accessor.customAccessorIdentifier}"
        val accessedType: DataTypeRef
            get() = accessor.objectType
    }

    data class ConfiguringLambdaReceiver(
        override val function: SchemaFunction,
        override val parameterBindings: ParameterValueBinding,
        override val invocationId: OperationId,
        val lambdaReceiverType: DataTypeRef,
        override val originElement: LanguageTreeElement,
        override val receiver: ObjectOrigin,
    ) : FunctionInvocationOrigin, HasReceiver, ReceiverOrigin {
        init {
            val semantics = function.semantics
            require(semantics is FunctionSemantics.AccessAndConfigure && semantics.accessor is ConfigureAccessor.ConfiguringLambdaArgument)
        }

        override fun toString(): String {
            val functionInvocationString = functionInvocationString(function, receiver, invocationId, parameterBindings)
            return "$functionInvocationString{}-receiver"
        }
    }

    data class PropertyDefaultValue(
        override val receiver: ObjectOrigin,
        val property: DataProperty,
        override val originElement: LanguageTreeElement
    ) : ObjectOrigin, HasReceiver {
        override fun toString(): String = "$receiver${'.'}${property.name}_default"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PropertyDefaultValue) return false

            if (receiver != other.receiver) return false
            if (property != other.property) return false

            return true
        }

        override fun hashCode(): Int {
            var result = receiver.hashCode()
            result = 31 * result + property.hashCode()
            return result
        }
    }

    data class External(val key: ExternalObjectProviderKey, override val originElement: PropertyAccess) : ObjectOrigin {
        override fun toString(): String = "${key.objectType}"
    }
}


data class ParameterValueBinding(
    val bindingMap: Map<DataParameter, ObjectOrigin>,
    val providesConfigureBlock: Boolean
)


private
fun functionInvocationString(function: SchemaFunction, receiver: ObjectOrigin?, invocationId: OperationId, parameterBindings: ParameterValueBinding) =
    receiver?.toString()?.plus(".").orEmpty() + buildString {
        if (function is DataConstructor) {
            val fqn = when (val ref = function.dataClass) {
                is DataTypeRef.Name -> ref.fqName.toString()
                is DataTypeRef.Type -> (ref.dataType as? DataClass)?.name?.qualifiedName
                    ?: ref.dataType.toString()
            }
            append(fqn)
            append(".")
        }
        append(function.simpleName)
        append("#")
        append(invocationId)
        append("(")
        append(parameterBindings.bindingMap.entries.joinToString { (k, v) -> "${k.name} = $v" })
        append(")")
        if (parameterBindings.providesConfigureBlock) {
            append(" { ... }")
        }
    }
