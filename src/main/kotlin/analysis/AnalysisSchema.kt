package com.h0tk3y.kotlin.staticObjectNotation.analysis

import kotlin.reflect.KClass

data class AnalysisSchema(
    val topLevelReceiverType: DataType.DataClass<*>,
    val dataClassesByFqName: Map<FqName, DataType.DataClass<*>>,
    val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    val defaultImports: Set<FqName>,
)

sealed interface DataType {
    interface ConstantType<JvmType> : DataType
    data object IntDataType : ConstantType<Int>
    data object LongDataType : ConstantType<Long>
    data object StringDataType : ConstantType<String>
    data object BooleanDataType : ConstantType<Boolean>
    
    // TODO: implement nulls?
    data object NullType : DataType

    // TODO: `Any` type? 
    // TODO: Support subtyping of some sort in the schema rather than via reflection?

    data class DataClass<JvmDataClass : Any>(
        val kClass: KClass<out JvmDataClass>,
        val properties: List<DataProperty>,
        val memberFunctions: List<SchemaMemberFunction>,
        private val constructorSignatures: List<DataConstructorSignature>,
    ) : DataType {
        val constructors: List<DataConstructor>
            get() = constructorSignatures.map { DataConstructor(it.parameters, DataTypeRef.Type(this)) }

        override fun toString(): String = "${kClass.qualifiedName}"
    }
}

data class DataProperty(
    val name: String,
    val type: DataTypeRef,
    val isReadOnly: Boolean
)

sealed interface SchemaFunction {
    val simpleName: String
    val semantics: FunctionSemantics
    val parameters: List<DataParameter>
    val returnValueType: DataTypeRef get() = semantics.returnValueType
}

sealed interface SchemaMemberFunction : SchemaFunction {
    override val simpleName: String
    val receiver: DataTypeRef
}

data class DataBuilderFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    val dataParameter: DataParameter,
) : SchemaMemberFunction {
    override val semantics: FunctionSemantics.Builder = FunctionSemantics.Builder(receiver)
    override val parameters: List<DataParameter>
        get() = listOf(dataParameter)
}

data class DataTopLevelFunction(
    val packageName: String,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val semantics: FunctionSemantics.NewObjectFunctionSemantics,
) : SchemaFunction

data class DataMemberFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val semantics: FunctionSemantics
) : SchemaMemberFunction {
    init {
        if (semantics is FunctionSemantics.AccessAndConfigure) {
            if (semantics.accessor is ConfigureAccessor.Property) {
                require(semantics.accessor.receiver == receiver)
            }
        }
    }
}

data class DataConstructorSignature(
    val parameters: List<DataParameter>
)

data class DataConstructor(
    override val parameters: List<DataParameter>,
    val dataClass: DataTypeRef
) : SchemaFunction {
    override val simpleName get() = "<init>"
    override val semantics: FunctionSemantics = FunctionSemantics.Pure(dataClass)
}

data class DataParameter(
    val name: String?,
    val type: DataTypeRef,
    val isDefault: Boolean,
    val semantics: ParameterSemantics
)

sealed interface ParameterSemantics {
    data class StoreValueInProperty(val dataProperty: DataProperty) : ParameterSemantics
    data object Unknown : ParameterSemantics
}

sealed interface FunctionSemantics {
    sealed interface ConfigureSemantics : FunctionSemantics
    sealed interface NewObjectFunctionSemantics : FunctionSemantics

    val returnValueType: DataTypeRef

    class Builder(private val objectType: DataTypeRef) : FunctionSemantics {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    class AccessAndConfigure(val accessor: ConfigureAccessor) : ConfigureSemantics {
        override val returnValueType: DataTypeRef
            get() = accessor.objectType
    }

    class AddAndConfigure(val objectType: DataTypeRef) : NewObjectFunctionSemantics, ConfigureSemantics {
        override val returnValueType: DataTypeRef
            get() = objectType
    }
    
    class Pure(override val returnValueType: DataTypeRef) : NewObjectFunctionSemantics
}

sealed interface ConfigureAccessor {
    val objectType: DataTypeRef

    data class Property(val receiver: DataTypeRef, val dataProperty: DataProperty) : ConfigureAccessor {
        override val objectType: DataTypeRef
            get() = dataProperty.type
    }
    
    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}

data class FqName(val packageName: String, val simpleName: String) {
    companion object {
        fun parse(fqNameString: String): FqName {
            val parts = fqNameString.split(".")
            return FqName(parts.dropLast(1).joinToString("."), parts.last())
        }
    }

    override fun toString(): String = "$packageName.$simpleName"
}

val DataTopLevelFunction.fqName: FqName get() = FqName(packageName, simpleName)

data class ExternalObjectProviderKey(val type: DataType)

sealed interface DataTypeRef {
    data class Type(val type: DataType) : DataTypeRef
    data class Name(val fqName: FqName) : DataTypeRef
}

val DataType.ref: DataTypeRef get() = DataTypeRef.Type(this)