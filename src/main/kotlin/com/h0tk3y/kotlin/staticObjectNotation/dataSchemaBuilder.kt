package com.h0tk3y.kotlin.staticObjectNotation

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataBuilderFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataConstructorSignature
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataParameter
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataProperty
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTopLevelFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ExternalObjectProviderKey
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.fqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ref
import com.h0tk3y.kotlin.staticObjectNotation.types.isConfigureLambda
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf

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
    val extObjects = externalObjects.map { (key, value) -> key to ExternalObjectProviderKey(value.toDataTypeRef()) }.toMap()
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
            val properties = extractProperties(type)
            properties.forEach { addProperty(type, DataProperty(it.name, it.returnType, it.isReadOnly, it.hasDefaultValue)) }
        }
    }
}

fun createDataType(
    kClass: KClass<*>,
    preIndex: PreIndex
): DataType.DataClass<*> {
    val properties = preIndex.getAllProperties(kClass)

    val functions = kClass.memberFunctions
        .filter { it.isIncluded && it.visibility == KVisibility.PUBLIC && !it.isIgnored }
        .map { function ->
            memberFunction(kClass, function, preIndex)
        }
    return DataType.DataClass(kClass, properties, functions, constructors(kClass, preIndex))
}

private fun constructors(kClass: KClass<*>, preIndex: PreIndex): List<DataConstructorSignature> =
    kClass.constructors.filter { it.isIncluded }.map { constructor ->
        val params = constructor.parameters
        val dataParams = params.map { param ->
            dataParameter(constructor, param, kClass, FunctionSemantics.Pure(kClass.toDataTypeRef()), preIndex)
        }
        DataConstructorSignature(dataParams)
    }

private fun extractProperties(kClass: KClass<*>) =
    (propertiesFromAccessorsOf(kClass) + memberPropertiesOf(kClass)).distinctBy { it }

private fun memberPropertiesOf(kClass: KClass<*>): List<CollectedPropertyInformation> = kClass.memberProperties
    .filter { property ->
        (property.isIncluded || kClass.primaryConstructor?.parameters.orEmpty()
            .any { it.name == property.name && it.type == property.returnType })
                && property.visibility == KVisibility.PUBLIC
    }.map { property -> kPropertyInformation(property) }

private fun propertiesFromAccessorsOf(kClass: KClass<*>): List<CollectedPropertyInformation> {
    val functionsByName = kClass.memberFunctions.groupBy { it.name }
    val getters = functionsByName
        .filterKeys { it.startsWith("get") && it.substringAfter("get").first().isUpperCase() }
        .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.all { it == fn.instanceParameter } } }
        .filterValues { it != null && it.isIncluded }
    return getters.map { (name, getter) ->
        checkNotNull(getter)
        val nameAfterGet = name.substringAfter("get")
        val propertyName = nameAfterGet.decapitalize()
        val type = getter.returnType.toDataTypeRefOrError()
        val hasSetter = functionsByName["set$nameAfterGet"].orEmpty().any { fn -> fn.parameters.singleOrNull { it != fn.instanceParameter }?.type == getter.returnType }
        CollectedPropertyInformation(propertyName, type, !hasSetter, true)
    }
}

private fun kPropertyInformation(property: KProperty<*>): CollectedPropertyInformation {
    val isReadOnly = property !is KMutableProperty<*>
    return CollectedPropertyInformation(
        property.name,
        property.returnType.toDataTypeRefOrError(),
        isReadOnly,
        hasDefaultValue = run {
            isReadOnly || property.annotationsWithGetters.any { it is HasDefaultValue }
        }
    )
}

private data class CollectedPropertyInformation(
    val name: String,
    val returnType: DataTypeRef,
    val isReadOnly: Boolean,
    val hasDefaultValue: Boolean
)

private fun dataTopLevelFunction(
    function: KFunction<*>,
    preIndex: PreIndex
): DataTopLevelFunction {
    check(function.instanceParameter == null)

    val returnType = function.returnType
    checkInScope(returnType, preIndex)

    val returnTypeClassifier = function.returnType
    val semanticsFromSignature = FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())

    val fnParams = function.parameters
    val params = fnParams.filterIndexed { index, it ->
        index != fnParams.lastIndex || !isConfigureLambda(it, returnTypeClassifier)
    }.map { dataParameter(function, it, function.returnType.toKClass(), semanticsFromSignature, preIndex) }

    return DataTopLevelFunction(
        function.javaMethod!!.declaringClass.packageName,
        function.name,
        params,
        semanticsFromSignature
    )
}

private fun memberFunction(
    inType: KClass<*>,
    function: KFunction<*>,
    preIndex: PreIndex
): SchemaMemberFunction {
    val thisTypeRef = inType.toDataTypeRef()

    val returnType = function.returnType

    checkInScope(returnType, preIndex)
    val returnClass = function.returnType.classifier as KClass<*>
    val fnParams = function.parameters

    val semanticsFromSignature = inferFunctionSemanticsFromSignature(function, function.returnType, inType, preIndex)
    val maybeConfigureType = if (semanticsFromSignature is FunctionSemantics.AccessAndConfigure) {
        // TODO: be careful with non-class types?
        inType.memberProperties.find { it.name == function.name }?.returnType
    } else null

    val params = fnParams
        .filterIndexed { index, it ->
            it != function.instanceParameter && run {
                index != fnParams.lastIndex || !isConfigureLambda(it, maybeConfigureType ?: function.returnType)
            }
        }
        .map { fnParam -> dataParameter(function, fnParam, returnClass, semanticsFromSignature, preIndex) }

    return if (semanticsFromSignature is FunctionSemantics.Builder) {
        DataBuilderFunction(
            thisTypeRef,
            function.name,
            params.single()
        )
    } else {
        DataMemberFunction(
            thisTypeRef,
            function.name,
            params,
            semanticsFromSignature
        )
    }
}

private fun inferFunctionSemanticsFromSignature(
    function: KFunction<*>,
    returnTypeClassifier: KType,
    inType: KClass<*>?,
    preIndex: PreIndex
): FunctionSemantics {
    return when {
        function.annotations.any { it is Builder } -> {
            check(inType != null)
            FunctionSemantics.Builder(returnTypeClassifier.toDataTypeRefOrError())
        }

        function.annotations.any { it is Adding } -> {
            check(inType != null)
            val hasConfigureLambda =
                isConfigureLambda(function.parameters[function.parameters.lastIndex], function.returnType)
            FunctionSemantics.AddAndConfigure(returnTypeClassifier.toDataTypeRefOrError(), hasConfigureLambda)
        }
        function.annotations.any { it is Configuring } -> {
            check(inType != null)

            val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
            check(annotation != null)
            val propertyName = annotation.propertyName.ifEmpty { function.name }
            val kProperty = inType.memberProperties.find { it.name == propertyName }
            check(kProperty != null)
            val property = preIndex.getProperty(inType, propertyName)
            check(property != null)

            val hasConfigureLambda =
                isConfigureLambda(function.parameters[function.parameters.lastIndex], kProperty.returnType)

            check(hasConfigureLambda)
            val returnType = when (function.returnType) {
                typeOf<Unit>() -> FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
                kProperty.returnType -> FunctionSemantics.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT
                else -> error("cannot infer the return type of a configuring function; it must be Unit or the configured object type")
            }
            FunctionSemantics.AccessAndConfigure(ConfigureAccessor.Property(inType.toDataTypeRef(), property), returnType)
        }

        else -> FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())
    }
}

private fun dataParameter(
    function: KFunction<*>,
    fnParam: KParameter,
    ownerClass: KClass<*>,
    functionSemantics: FunctionSemantics,
    preIndex: PreIndex
): DataParameter {
    val paramType = fnParam.type
    checkInScope(paramType, preIndex)
    val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, ownerClass, preIndex)
    return DataParameter(fnParam.name, paramType.toDataTypeRefOrError(), fnParam.isOptional, paramSemantics)
}

private fun getParameterSemantics(
    functionSemantics: FunctionSemantics,
    function: KFunction<*>,
    fnParam: KParameter,
    ownerClass: KClass<*>,
    preIndex: PreIndex
): ParameterSemantics {
    val propertyNamesToCheck = buildList {
        if (functionSemantics is FunctionSemantics.Builder) add(function.name)
        if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
    }
    propertyNamesToCheck.forEach { propertyName ->
        val isPropertyLike =
            ownerClass.memberProperties.any {
                it.visibility == KVisibility.PUBLIC &&
                        it.name == propertyName &&
                        it.returnType == fnParam.type
            }
        if (isPropertyLike) {
            val storeProperty = checkNotNull(preIndex.getProperty(ownerClass, propertyName))
            return ParameterSemantics.StoreValueInProperty(storeProperty)
        }
    }
    return ParameterSemantics.Unknown
}

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

private fun KType.toDataTypeRef(): DataTypeRef? = when {
    // isMarkedNullable -> TODO: support nullable types
    arguments.isNotEmpty() -> null // TODO: support for some particular generic types
    else -> when (val classifier = classifier) {
        null -> null
        else -> classifier.toDataTypeRef()
    }
}

private fun KType.toDataTypeRefOrError() =
    toDataTypeRef()
        ?: error("failed to convert type $this to data type")

private fun KClassifier.toDataTypeRef(): DataTypeRef =
    when (this) {
        Unit::class -> DataType.UnitType.ref
        Int::class -> DataType.IntDataType.ref
        String::class -> DataType.StringDataType.ref
        Boolean::class -> DataType.BooleanDataType.ref
        Long::class -> DataType.LongDataType.ref
        is KClass<*> -> DataTypeRef.Name(FqName.parse(java.name))
        else -> error("unexpected type")
    }

val KCallable<*>.isIncluded
    get() = this.annotationsWithGetters.any {
        it is Builder || it is Configuring || it is Adding || it is Restricted || it is HasDefaultValue
    }

val KCallable<*>.annotationsWithGetters: List<Annotation>
    get() = this.annotations + if (this is KProperty) this.getter.annotations else emptyList()

private fun String.decapitalize() = first().lowercase(Locale.ENGLISH) + drop(1)

fun KType.toKClass() = (classifier ?: error("unclassifiable type $this is used in the schema")) as? KClass<*>
    ?: error("type $this classified as a non-class is used in the schema")
