package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import kotlin.reflect.*
import kotlin.reflect.full.*

fun schemaFromTypes(topLevelReceiver: KClass<*>, types: List<KClass<*>>): AnalysisSchema {
    val preIndex = createPreIndex(types)

    val types = types.map { createDataType(it, preIndex) }

    return AnalysisSchema(
        types.single { it.kClass == topLevelReceiver },
        types.associateBy { FqName.parse(it.kClass.qualifiedName!!) },
        emptyMap(),
        emptyMap(),
        emptySet()
    )
}

class PreIndex {
    private val properties = mutableMapOf<KClass<*>, MutableMap<String, DataProperty>>()

    fun addType(kClass: KClass<*>) {
        properties.getOrPut(kClass) { mutableMapOf() }
    }

    fun addProperty(kClass: KClass<*>, property: DataProperty) {
        properties.getOrPut(kClass) { mutableMapOf() }[property.name] = property
    }

    fun hasType(kClass: KClass<*>): Boolean = kClass in properties

    fun getAllProperties(kClass: KClass<*>): List<DataProperty> = properties[kClass]?.values.orEmpty().toList()

    fun getProperty(kClass: KClass<*>, name: String) = properties[kClass]?.get(name)
}

fun createPreIndex(types: List<KClass<*>>): PreIndex {
    return PreIndex().apply {
        types.forEach { type ->
            addType(type)
            val properties = dataPropertiesOf(type)
            properties.forEach { addProperty(type, it) }
        }
    }
}

fun createDataType(
    kClass: KClass<*>,
    preIndex: PreIndex
): DataType.DataClass<*> {
    val thisTypeRef = typeToRef(kClass)
    val properties = preIndex.getAllProperties(kClass)

    val functions = kClass.memberFunctions
        .filter { it.visibility == KVisibility.PUBLIC && !it.isIgnored }
        .map { function ->
            dataMemberFunction(kClass, function, thisTypeRef, preIndex)
        }
    return DataType.DataClass(kClass, properties, functions, constructors(kClass, preIndex))
}

private fun constructors(kClass: KClass<*>, preIndex: PreIndex): List<DataConstructorSignature> =
    kClass.constructors.map { constructor ->
        val params = constructor.parameters
        val dataParams = params.mapIndexedNotNull { index, param ->
            dataParameterIfNotLambda(param, kClass, preIndex)
        }
        DataConstructorSignature(dataParams)
    }

private fun dataPropertiesOf(kClass: KClass<*>) = kClass.memberProperties
    .filter { it.visibility == KVisibility.PUBLIC }
    .map { property ->
        val typeClassifier = property.returnType.classifier
            ?: error("cannot get a classifier for property return type")
        DataProperty(property.name, typeToRef(typeClassifier), property is KMutableProperty<*>)
    }

private fun dataMemberFunction(
    inType: KClass<*>,
    function: KFunction<*>,
    thisTypeRef: DataTypeRef,
    preIndex: PreIndex
): DataMemberFunction {
    val returnType = function.returnType
    val returnTypeClassifier = function.returnType.classifier

    checkInScope(returnType, preIndex)
    val returnClass = returnTypeClassifier as KClass<*>
    val fnParams = function.parameters

    val params = fnParams.mapIndexedNotNull { index, fnParam ->
        if (fnParam == function.instanceParameter) null else {
            dataParameterIfNotLambda(
                fnParam,
                returnClass,
                preIndex
            )
        }
    }

    val returnDataType = typeToRef(returnTypeClassifier)

    val semantics: FunctionSemantics = when {
        function.annotations.any { it is Adding } -> {
            FunctionSemantics.AddAndConfigure(returnDataType)
        }

        function.annotations.any { it is Builder } -> {
            check(params.size == 1)
            check(inType == returnType.classifier)
            FunctionSemantics.Builder(returnDataType)
        }

        function.annotations.any { it is Configuring } -> {
            val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
            check(annotation != null)
            val propertyName = if (annotation.propertyName.isEmpty()) function.name else annotation.propertyName
            val kProperty = inType.memberProperties.find { it.name == propertyName }
            check(kProperty != null)
            val property = preIndex.getProperty(inType, propertyName)
            check(property != null)

            val hasConfigureLambda = function.parameters.withIndex().any { (index, it) ->
                isConfigureLambda(
                    it,
                    isLast = index == function.parameters.lastIndex,
                    kProperty.returnType.classifier as KClass<*>
                )
            }

            check(hasConfigureLambda)
            FunctionSemantics.AccessAndConfigure(ConfigureAccessor.Property(typeToRef(inType), property))
        }

        else -> FunctionSemantics.Pure(returnDataType)
    }
    return DataMemberFunction(thisTypeRef, function.name, params, semantics)
}

private fun dataParameterIfNotLambda(
    fnParam: KParameter,
    returnClass: KClass<*>,
    preIndex: PreIndex
): DataParameter? {
    val paramType = fnParam.type

    val isConfigureLambda = paramType.isSubtypeOf(
        Function1::class.createType(listOf(KTypeProjection(null, null), KTypeProjection(null, null)))
    )
    
    return if (isConfigureLambda) {
        null
    } else {
        checkInScope(paramType, preIndex)

        val isPropertyLike =
            returnClass.memberProperties.any { it.name == fnParam.name && it.returnType == fnParam.type }

        val paramSemantics = if (isPropertyLike) {
            val storeProperty = preIndex.getProperty(returnClass, fnParam.name.orEmpty())
            if (storeProperty != null) {
                ParameterSemantics.StoreValueInProperty(storeProperty)
            } else ParameterSemantics.UsedExternally
        } else {
            ParameterSemantics.UsedExternally
        }

        DataParameter(fnParam.name, typeToRef(paramType.classifier as KClass<*>), fnParam.isOptional, paramSemantics)
    }
}

private fun isConfigureLambda(kParam: KParameter, isLast: Boolean, returnTypeClassifier: KClass<*>): Boolean {
    val paramType = kParam.type
    return isLast && paramType.isSubtypeOf(configureLambdaTypeFor(returnTypeClassifier))
}

private fun configureLambdaTypeFor(returnTypeClassifier: KClass<*>) =
    Function1::class.createType(
        listOf(
            KTypeProjection(KVariance.INVARIANT, returnTypeClassifier.createType()),
            KTypeProjection(KVariance.INVARIANT, Unit::class.createType())
        )
    )

private fun checkInScope(
    type: KType,
    typesScope: PreIndex
) {
    if (type.classifier?.isInScope(typesScope) != true) {
        error("type ${type} used in a function is not in schema scope")
    }
}

fun KClassifier.isInScope(typesScope: PreIndex) =
    isBuiltInType || this is KClass<*> && typesScope.hasType(this)

val KClassifier.isBuiltInType: Boolean
    get() = when (this) {
        Int::class, String::class, Boolean::class, Long::class, Unit::class -> true
        else -> false
    }

val KFunction<*>.isIgnored: Boolean
    get() = when (this.name) {
        // TODO: match precisely
        Any::toString.name, Any::equals.name, Any::hashCode.name -> true
        else -> false
    }

fun typeToRef(kType: KClassifier): DataTypeRef = when (kType) {
    Int::class -> DataType.IntDataType.ref
    String::class -> DataType.StringDataType.ref
    Boolean::class -> DataType.BooleanDataType.ref
    Long::class -> DataType.LongDataType.ref
    is KClass<*> -> DataTypeRef.Name(FqName.parse(kType.java.name))
    else -> error("unexpected type")
}