package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.*

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
    val assignmentOrder: Long,
    val assignmentMethod: AssignmentMethod
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

    data class TopLevelReceiver(val type: DataType, override val originElement: LanguageTreeElement) : ObjectOrigin {
        override fun toString(): String = "(top-level-object)"
    }

    data class FromLocalValue(val localValue: LocalValue, val assigned: ObjectOrigin) : ObjectOrigin {
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
        val invocationId: Long
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
        override val invocationId: Long
    ) : FunctionInvocationOrigin, HasReceiver {
        override fun toString(): String = receiver.toString()
    }

    data class NewObjectFromMemberFunction(
        override val function: SchemaMemberFunction,
        override val receiver: ObjectOrigin,
        override val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        override val invocationId: Long
    ) : FunctionInvocationOrigin, HasReceiver {
        override fun toString(): String =
            functionInvocationString(function, receiver, invocationId, parameterBindings)
    }

    data class NewObjectFromTopLevelFunction(
        override val function: SchemaFunction,
        override val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        override val invocationId: Long
    ) : FunctionInvocationOrigin {
        override val receiver: ObjectOrigin? get() = null

        override fun toString(): String = functionInvocationString(function, null, invocationId, parameterBindings)
    }

    data class ConfigureReceiver(
        override val receiver: ObjectOrigin,
        override val function: SchemaFunction,
        override val originElement: FunctionCall,
        override val invocationId: Long,
        val accessor: ConfigureAccessor,
    ) : FunctionOrigin {
        override fun toString(): String {
            val accessorString = when (accessor) {
                is ConfigureAccessor.Property -> accessor.dataProperty.name
            }
            return "$receiver.$accessorString"
        }
    }

    data class PropertyReference(
        val receiver: ObjectOrigin,
        val property: DataProperty,
        override val originElement: LanguageTreeElement
    ) : ObjectOrigin {
        override fun toString(): String = "$receiver${'.'}${property.name}"
    }

    data class PropertyDefaultValue(
        val receiver: ObjectOrigin,
        val property: DataProperty,
        override val originElement: LanguageTreeElement
    ) : ObjectOrigin {
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
        override fun toString(): String = "${key.type}"
    }
}

data class ParameterValueBinding(val bindingMap: Map<DataParameter, ObjectOrigin>)

private fun functionInvocationString(function: SchemaFunction, receiver: ObjectOrigin?, invocationId: Long, parameterBindings: ParameterValueBinding) =
    receiver?.toString()?.plus(".").orEmpty() + buildString {
        if (function is DataConstructor) {
            val fqn = when (val ref = function.dataClass) {
                is DataTypeRef.Name -> ref.fqName.toString()
                is DataTypeRef.Type -> (ref.type as? DataType.DataClass<*>)?.kClass?.qualifiedName
                    ?: ref.type.toString()
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
    }
