package com.h0tk3y.kotlin.staticObjectNotation

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaMethod

fun schemaFromTypes(
    topLevelReceiver: KClass<*>,
    types: List<KClass<*>>,
    externalFunctions: List<KFunction<*>> = emptyList(),
    externalObjects: Map<FqName, KClass<*>> = emptyMap(),
    defaultImports: List<FqName> = emptyList(),
): AnalysisSchema {
    val preIndex = createPreIndex(types)

    val dataTypes = types.map { createDataType(it, preIndex) }

    val extFunctions = externalFunctions.map { dataTopLevelFunction(it, preIndex) }.associateBy { it.fqName }
    val extObjects = externalObjects.map { (key, value) -> key to ExternalObjectProviderKey(typeToRef(value)) }.toMap()
    return AnalysisSchema(
        dataTypes.single { it.kClass == topLevelReceiver },
        dataTypes.associateBy { FqName.parse(it.kClass.qualifiedName!!) },
        extFunctions,
        extObjects,
        defaultImports.toSet ()
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
    val properties = preIndex.getAllProperties(kClass)

    val functions = kClass.memberFunctions
        .filter { it.visibility == KVisibility.PUBLIC && !it.isIgnored }
        .map { function ->
            dataMemberFunction(kClass, function, preIndex)
        }
    return DataType.DataClass(kClass, properties, functions, constructors(kClass, preIndex))
}

private fun constructors(kClass: KClass<*>, preIndex: PreIndex): List<DataConstructorSignature> =
    kClass.constructors.map { constructor ->
        val params = constructor.parameters
        val dataParams = params.map { param ->
            dataParameter(constructor, param, kClass, FunctionSemantics.Pure(typeToRef(kClass)), preIndex)
        }
        DataConstructorSignature(dataParams)
    }

private fun dataPropertiesOf(kClass: KClass<*>) = kClass.memberProperties
    .filter { it.visibility == KVisibility.PUBLIC }
    .map { property ->
        val typeClassifier = property.returnType.classifier
            ?: error("cannot get a classifier for property return type")
        val constructor = kClass.primaryConstructor 
            ?: error("classes with no primary constructor are not supported yet")
        val isReadOnly = property !is KMutableProperty<*>
        // TODO: a better predicate
        val hasDefaultValue = isReadOnly && constructor.parameters.none { it.name == property.name }
        DataProperty(property.name, typeToRef(typeClassifier), isReadOnly, hasDefaultValue)
    }

private fun dataTopLevelFunction(
    function: KFunction<*>,
    preIndex: PreIndex
): DataTopLevelFunction {
    check(function.instanceParameter == null)

    val returnType = function.returnType
    checkInScope(returnType, preIndex)

    val returnTypeClassifier = function.returnType.classifier as KClass<*>
    val semanticsFromSignature = FunctionSemantics.Pure(typeToRef(returnTypeClassifier))

    val fnParams = function.parameters
    val params = fnParams.filterIndexed { index, it ->
        index != fnParams.lastIndex || !isConfigureLambda(it, returnTypeClassifier)
    }.map { dataParameter(function, it, returnTypeClassifier, semanticsFromSignature, preIndex) }

    return DataTopLevelFunction(
        function.javaMethod!!.declaringClass.packageName,
        function.name,
        params,
        semanticsFromSignature
    )
}

private fun dataMemberFunction(
    inType: KClass<*>,
    function: KFunction<*>,
    preIndex: PreIndex
): DataMemberFunction {
    val thisTypeRef = typeToRef(inType)
    
    val returnType = function.returnType
    val returnTypeClassifier = function.returnType.classifier

    checkInScope(returnType, preIndex)
    val returnClass = returnTypeClassifier as KClass<*>
    val fnParams = function.parameters

    val semanticsFromSignature = inferFunctionSemanticsFromSignature(function, returnTypeClassifier, inType, preIndex)
    val maybeConfigureType = if (semanticsFromSignature is FunctionSemantics.AccessAndConfigure) {
        // TODO: be careful with non-class types?
        inType.memberProperties.find { it.name == function.name }?.returnType?.classifier as? KClass<*>
    } else null

    val params = fnParams
        .filterIndexed { index, it ->
            it != function.instanceParameter && run {
                index != fnParams.lastIndex || !isConfigureLambda(it, maybeConfigureType ?: returnTypeClassifier)
            }        
        }
        .map { fnParam -> dataParameter(function, fnParam, returnClass, semanticsFromSignature, preIndex) }

    return DataMemberFunction(
        thisTypeRef,
        function.name,
        params,
        semanticsFromSignature
    )
}

private fun inferFunctionSemanticsFromSignature(
    function: KFunction<*>,
    returnTypeClassifier: KClassifier?,
    inType: KClass<*>?,
    preIndex: PreIndex
): FunctionSemantics {
    val returnDataType = typeToRef(returnTypeClassifier as KClassifier)
    return when {
        function.annotations.any { it is Builder } -> {
            check(inType != null)
            FunctionSemantics.Builder(returnDataType)
        }

        function.annotations.any { it is Adding } -> {
            check(inType != null)
            FunctionSemantics.AddAndConfigure(returnDataType)
        }
        function.annotations.any { it is Configuring } -> {
            check(inType != null)
            
            val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
            check(annotation != null)
            val propertyName = annotation.propertyName.ifEmpty { function.name }
            val kProperty = inType.memberProperties.find { it.name == propertyName }
            check(kProperty != null)
            val propertyTypeClassifier = kProperty.returnType.classifier as KClass<*>
            val property = preIndex.getProperty(inType, propertyName)
            check(property != null)

            val hasConfigureLambda = function.parameters.withIndex().any { (index, it) ->
                index == function.parameters.lastIndex && isConfigureLambda(it, propertyTypeClassifier)
            }

            check(hasConfigureLambda)
            val returnType = when (function.returnType) {
                typeOf<Unit>() -> FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
                kProperty.returnType -> FunctionSemantics.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT
                else -> error("cannot infer the return type of a configuring function; it must be Unit or the configured object type")
            }
            FunctionSemantics.AccessAndConfigure(ConfigureAccessor.Property(typeToRef(inType), property), returnType)
        }

        else -> FunctionSemantics.Pure(returnDataType)
    }
}

private fun dataParameter(
    function: KFunction<*>,
    fnParam: KParameter,
    returnClass: KClass<*>,
    functionSemantics: FunctionSemantics,
    preIndex: PreIndex
): DataParameter {
    val paramType = fnParam.type
    checkInScope(paramType, preIndex)
    val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, returnClass, preIndex)
    return DataParameter(fnParam.name, typeToRef(paramType.classifier as KClass<*>), fnParam.isOptional, paramSemantics)
}

private fun getParameterSemantics(
    functionSemantics: FunctionSemantics,
    function: KFunction<*>,
    fnParam: KParameter,
    returnClass: KClass<*>,
    preIndex: PreIndex
): ParameterSemantics {
    val propertyNamesToCheck = buildList {
        if (functionSemantics is FunctionSemantics.Builder) add(function.name)
        if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
    }
    propertyNamesToCheck.forEach { propertyName ->
        val isPropertyLike =
            returnClass.memberProperties.any {
                it.visibility == KVisibility.PUBLIC &&
                        it.name == propertyName &&
                        it.returnType == fnParam.type
            }
        if (isPropertyLike) {
            val storeProperty = checkNotNull(preIndex.getProperty(returnClass, propertyName))
            return ParameterSemantics.StoreValueInProperty(storeProperty)
        }
    }
    return ParameterSemantics.Unknown
}

private fun isConfigureLambda(kParam: KParameter, returnTypeClassifier: KClass<*>): Boolean {
    val paramType = kParam.type
    return paramType.isSubtypeOf(configureLambdaTypeFor(returnTypeClassifier))
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
    typeScope: PreIndex
) {
    if (type.classifier?.isInScope(typeScope) != true) {
        error("type $type used in a function is not in schema scope")
    }
}

fun KClassifier.isInScope(typeScope: PreIndex) =
    isBuiltInType || this is KClass<*> && typeScope.hasType(this)

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