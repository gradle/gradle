package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.ConfigureLambdaHandler

data class AnalysisSchema(
    val topLevelReceiverType: DataType.DataClass,
    val dataClassesByFqName: Map<FqName, DataType.DataClass>,
    val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    val defaultImports: Set<FqName>,
    val configureLambdas: ConfigureLambdaHandler
)

sealed interface DataType {
    sealed interface ConstantType<JvmType> : DataType
    data object IntDataType : ConstantType<Int> {
        override fun toString(): String = "Int"
    }
    data object LongDataType : ConstantType<Long> {
        override fun toString(): String = "Long"
    }
    data object StringDataType : ConstantType<String> {
        override fun toString(): String = "String"
    }
    data object BooleanDataType : ConstantType<Boolean> {
        override fun toString(): String = "Boolean"
    }

    // TODO: implement nulls?
    data object NullType : DataType

    data object UnitType : DataType

    // TODO: `Any` type?
    // TODO: Support subtyping of some sort in the schema rather than via reflection?

    data class DataClass(
        val name: FqName,
        val supertypes: Set<FqName>,
        val properties: List<DataProperty>,
        val memberFunctions: List<SchemaMemberFunction>,
        val constructors: List<DataConstructor>
    ) : DataType {
        override fun toString(): String = name.simpleName
    }
}

data class DataProperty(
    val name: String,
    val type: DataTypeRef,
    val isReadOnly: Boolean,
    val hasDefaultValue: Boolean,
    val isHiddenInDsl: Boolean = false,
    val isDirectAccessOnly: Boolean = false
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
    val isDirectAccessOnly: Boolean
}

data class DataBuilderFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val isDirectAccessOnly: Boolean,
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
    override val semantics: FunctionSemantics.Pure,
) : SchemaFunction

data class DataMemberFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val isDirectAccessOnly: Boolean,
    override val semantics: FunctionSemantics,
) : SchemaMemberFunction {
    init {
        if (semantics is FunctionSemantics.AccessAndConfigure) {
            if (semantics.accessor is ConfigureAccessor.Property) {
                require(semantics.accessor.receiver == receiver)
            }
        }
    }
}

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

    class AccessAndConfigure(
        val accessor: ConfigureAccessor,
        val returnType: ReturnType
    ) : ConfigureSemantics {
        enum class ReturnType {
            UNIT, CONFIGURED_OBJECT
        }

        override val returnValueType: DataTypeRef
            get() = when (returnType) {
                ReturnType.UNIT -> DataType.UnitType.ref
                ReturnType.CONFIGURED_OBJECT -> accessor.objectType
            }
    }

    class AddAndConfigure(
        private val objectType: DataTypeRef,
        val acceptsConfigureBlock: Boolean
    ) : NewObjectFunctionSemantics, ConfigureSemantics {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    class Pure(override val returnValueType: DataTypeRef) : NewObjectFunctionSemantics
}

sealed interface ConfigureAccessor {
    val objectType: DataTypeRef

    fun access(objectOrigin: ObjectOrigin): ObjectOrigin

    data class Property(val receiver: DataTypeRef, val dataProperty: DataProperty) : ConfigureAccessor {
        override val objectType: DataTypeRef
            get() = dataProperty.type

        override fun access(objectOrigin: ObjectOrigin): ObjectOrigin = ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
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

    val qualifiedName get() = "$packageName.$simpleName"

    override fun toString(): String = qualifiedName
}

val DataTopLevelFunction.fqName: FqName get() = FqName(packageName, simpleName)

data class ExternalObjectProviderKey(val type: DataTypeRef)

sealed interface DataTypeRef {
    data class Type(val type: DataType) : DataTypeRef
    data class Name(val fqName: FqName) : DataTypeRef
}

val DataType.ref: DataTypeRef get() = DataTypeRef.Type(this)
