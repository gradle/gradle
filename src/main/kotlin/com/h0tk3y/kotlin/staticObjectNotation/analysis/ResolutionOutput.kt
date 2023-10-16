package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.evaluation.DataValue
import com.h0tk3y.kotlin.staticObjectNotation.language.*

sealed interface AssignmentResolution {
    data class AssignProperty(val propertyReference: PropertyReferenceResolution) : AssignmentResolution
    data class ReassignLocalVal(val localValue: LocalValue) : AssignmentResolution
}

// TODO: report failures to resolve with potential candidates that could not work
data class PropertyReferenceResolution(
    val receiverObject: ObjectOrigin,
    val property: DataProperty
) {
    override fun toString(): String = "$receiverObject${'.'}${property.name}"
}

sealed interface ObjectOrigin {
    val originElement: LanguageTreeElement

    data class TopLevelReceiver(val type: DataType, override val originElement: LanguageTreeElement) : ObjectOrigin {
        override fun toString(): String = "(top-level-object)"
    }

    data class FromLocalValue(val localValue: LocalValue, val assigned: ObjectOrigin) : ObjectOrigin {
        override val originElement: LanguageTreeElement
            get() = assigned.originElement

        override fun toString(): String = "(val ${localValue.name} = $assigned)"
    }

    data class ConstantOrigin(val constant: DataValue.Constant<*>) : ObjectOrigin {
        override val originElement: LanguageTreeElement
            get() = constant.originElement

        override fun toString(): String = "${constant.value.let { if (it is String) "\"$it\"" else it }}"
    }
    
    data class NullObjectOrigin(override val originElement: Null) : ObjectOrigin
    
    sealed interface FunctionInvocationOrigin : ObjectOrigin {
        val function: SchemaFunction
        val receiverObject: ObjectOrigin?
    }
    
    data class BuilderReturnedReceiver(
        override val function: SchemaFunction,
        override val receiverObject: ObjectOrigin,
        override val originElement: FunctionCall,
        val parameterBindings: ParameterValueBinding,
        val invocationId: Long
    ) : FunctionInvocationOrigin {
        override fun toString(): String = receiverObject.toString()
    }

    data class NewObjectFromFunctionInvocation(
        override val function: SchemaFunction,
        override val receiverObject: ObjectOrigin?,
        val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        val invocationId: Long
    ) : FunctionInvocationOrigin {
        override fun toString(): String =
            receiverObject?.toString()?.plus(".").orEmpty() + buildString {
                if (function is DataConstructor) {
                    val fqn = when (val ref = function.dataClass) {
                        is DataTypeRef.Name -> ref.fqName.toString()
                        is DataTypeRef.Type -> (ref.type as? DataType.DataClass<*>)?.kClass?.qualifiedName ?: ref.type.toString()
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
    }
    
    data class ConfigureReceiver(
        override val receiverObject: ObjectOrigin,
        override val function: SchemaFunction,
        override val originElement: FunctionCall,
        val accessor: ConfigureAccessor,
    ) : FunctionInvocationOrigin {
        override fun toString(): String {
            val accessorString = when (accessor) {
                is ConfigureAccessor.Property -> accessor.dataProperty.name 
            }
            return "$receiverObject.$accessorString}"
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


