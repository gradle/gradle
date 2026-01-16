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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeSignature
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.Workarounds
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultEnumClass
import org.gradle.internal.declarativedsl.analysis.DefaultExternalObjectProviderKey
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.DefaultVarargSignature
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.TypeArgumentInternal
import org.gradle.internal.declarativedsl.analysis.fqName
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement.TagContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingTags.varargType
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.ContainerElement
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.PropertyType
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Special
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Supertype
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.UsedInMember
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

interface SchemaBuildingHost {
    val topLevelReceiverClass: KClass<*>

    fun classMembers(kClass: KClass<*>): ClassMembersForSchema
    fun declarativeSupertypesHierarchy(kClass: KClass<*>): Iterable<MaybeDeclarativeClassInHierarchy>

    fun containerTypeRef(kClass: KClass<*>): DataTypeRef
    fun modelTypeRef(kType: KType): DataTypeRef
    fun varargTypeRef(varargType: KType): DataTypeRef

    fun enterSchemaBuildingContext(contextElement: SchemaBuildingContextElement)
    fun leaveSchemaBuildingContext(contextElement: SchemaBuildingContextElement)
    val context: List<SchemaBuildingContextElement>
}

fun <R> SchemaBuildingHost.inContextOfModelClass(kClass: KClass<*>, doBuildSchema: () -> R): R =
    inContextOf(SchemaBuildingContextElement.ModelClassContextElement(kClass), doBuildSchema)

inline fun <R> SchemaBuildingHost.inContextOfModelMember(kCallable: KCallable<*>, doBuildSchema: () -> R): R =
    inContextOf(SchemaBuildingContextElement.ModelMemberContextElement(kCallable), doBuildSchema)

inline fun <R> SchemaBuildingHost.withTag(tagContextElement: TagContextElement, doBuildSchema: () -> R): R =
    inContextOf(tagContextElement, doBuildSchema)

sealed interface SchemaBuildingContextElement {
    val userRepresentation: String

    data class ModelClassContextElement(val kClass: KClass<*>) : SchemaBuildingContextElement {
        override val userRepresentation: String
            get() = "class '${kClass.qualifiedName}'"
    }

    data class ModelMemberContextElement(val kCallable: KCallable<*>) : SchemaBuildingContextElement {
        override val userRepresentation: String
            get() = "member '$kCallable'"
    }

    data class TagContextElement(val userVisibleTag: String) : SchemaBuildingContextElement {
        override val userRepresentation: String
            get() = userVisibleTag
    }
}

object SchemaBuildingTags {
    fun parameter(name: String) = TagContextElement("parameter '$name'")
    fun parameter(parameter: KParameter) = TagContextElement("parameter '${parameter.name ?: "(no name)"}'")
    fun parameter(parameter: SupportedKParameter) = TagContextElement("parameter '${parameter.name ?: "(no name)"}'")
    fun receiverType(kClass: KClass<*>) = TagContextElement("receiver type '$kClass'")
    fun varargType(kType: KType) = TagContextElement("vararg type '$kType'")
    fun returnValueType(kType: KType) = TagContextElement("return value type '$kType'")
    fun returnValueType(supportedType: SupportedTypeProjection.SupportedType) = TagContextElement("return value type '${supportedType.toKType()}'")
    fun externalObject(fqName: FqName) = TagContextElement("external object '${fqName.qualifiedName}'")
    fun namedDomainObjectContainer(name: String) = TagContextElement("nested named domain object container '$name'")
    fun elementTypeOfContainerSubtype(kClass: KClass<*>) = TagContextElement("element type of named domain object container subtype '$kClass'")
    fun typeArgument(argument: KTypeProjection) = TagContextElement("type argument '$argument'")
    fun configuredType(kType: KType) = TagContextElement("configured type '$kType'")
    fun configuredType(supportedType: SupportedTypeProjection.SupportedType) = TagContextElement("configured type '${supportedType.toKType()}'")

    fun schemaClass(dataType: DataType.ClassDataType) = TagContextElement("schema type '${dataType.name}'")
    fun schemaFunction(function: SchemaFunction) = with(function) { TagContextElement("schema function '${simpleName}(${parameters.joinToString { "${it.name}: ${it.type}" }}): ${returnValueType}'") }
    fun schemaProperty(property: DataProperty) = TagContextElement("schema property '${property.name}: ${property.valueType}'")
    fun returnValueType(dataTypeRef: DataTypeRef) = TagContextElement("return value type '$dataTypeRef'")
    fun schemaParameter(dataParameter: DataParameter) = TagContextElement("parameter '${dataParameter.name}: ${dataParameter.type}'")
    fun configuredType(dataTypeRef: DataTypeRef) = TagContextElement("configured type '$dataTypeRef'")
    fun schemaTypeConstructor(constructor: DataConstructor) = TagContextElement("$constructor(${constructor.parameters.joinToString { "${it.name}: ${it.type}" }})")
}

inline fun <R> SchemaBuildingHost.inContextOf(contextElement: SchemaBuildingContextElement, doBuildSchema: () -> R): R =
    try {
        enterSchemaBuildingContext(contextElement)
        doBuildSchema()
    } finally {
        leaveSchemaBuildingContext(contextElement)
    }


class DataSchemaBuilder(
    private val typeDiscovery: TypeDiscovery,
    private val propertyExtractor: PropertyExtractor,
    private val functionExtractor: FunctionExtractor,
    private val augmentationsProvider: AugmentationsProvider
) {

    private class Host(override val topLevelReceiverClass: KClass<*>) : SchemaBuildingHost {
        val dataClassToKClass = mutableMapOf<DataClass, KClass<*>>()

        val typeSignatures = mutableMapOf<FqName, ParameterizedTypeSignature>()
        val typeInstances = mutableMapOf<FqName, MutableMap<List<TypeArgument>, ParameterizedTypeInstance>>()

        private val typeVariables = mutableMapOf<KTypeParameter, DataType.TypeVariableUsage>()
        private var nextTypeVariableId = AtomicLong()

        private val currentContextStack = mutableListOf<SchemaBuildingContextElement>()

        override val context: List<SchemaBuildingContextElement>
            get() = currentContextStack

        private val classMembersCache = mutableMapOf<KClass<*>, ClassMembersForSchema>()
        private val declarativeSupertypesCache = mutableMapOf<KClass<*>, Iterable<MaybeDeclarativeClassInHierarchy>>()

        override fun classMembers(kClass: KClass<*>): ClassMembersForSchema =
            classMembersCache.getOrPut(kClass) { collectMembersForSchema(this, kClass) }

        override fun declarativeSupertypesHierarchy(kClass: KClass<*>) =
            declarativeSupertypesCache.getOrPut(kClass) { collectDeclarativeSuperclassHierarchy(kClass) }

        override fun containerTypeRef(kClass: KClass<*>): DataTypeRef {
            if (kClass.typeParameters.isNotEmpty()) {
                schemaBuildingFailure("Cannot use the parameterized class '$kClass' as a configurable type")
            }
            return modelTypeRef(kClass.starProjectedType)
        }

        override fun modelTypeRef(kType: KType): DataTypeRef =
            typeRef(kType)

        override fun varargTypeRef(varargType: KType): DataTypeRef {
            val varargTypeSignature = typeSignatures.getOrPut(DefaultVarargSignature.name) { DefaultVarargSignature }

            val elementTypeRef = withTag(varargType(varargType)) {
                when (varargType) {
                    typeOf<IntArray>() -> DataTypeInternal.DefaultIntDataType.ref
                    typeOf<LongArray>() -> DataTypeInternal.DefaultLongDataType.ref
                    typeOf<BooleanArray>() -> DataTypeInternal.DefaultBooleanDataType.ref
                    else -> modelTypeRef(varargType.arguments.singleOrNull()?.type ?: schemaBuildingFailure("unexpected vararg type"))
                }
            }

            return registerTypeInstance(varargTypeSignature, listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(elementTypeRef))).ref
        }

        override fun enterSchemaBuildingContext(contextElement: SchemaBuildingContextElement) {
            currentContextStack.add(contextElement)
        }

        override fun leaveSchemaBuildingContext(contextElement: SchemaBuildingContextElement) {
            currentContextStack.removeLast().also {
                check(it === contextElement) { "Schema building context mismatch: expected $contextElement on top, got $it" }
            }
        }

        private fun typeRef(kType: KType): DataTypeRef {
            check(currentContextStack.isNotEmpty()) { "Cannot reference a type $kType outside of a context" }

            return when (val kClassifier = kType.classifier) {
                Unit::class -> DataTypeInternal.DefaultUnitType.ref
                Int::class -> DataTypeInternal.DefaultIntDataType.ref
                String::class -> DataTypeInternal.DefaultStringDataType.ref
                Boolean::class -> DataTypeInternal.DefaultBooleanDataType.ref
                Long::class -> DataTypeInternal.DefaultLongDataType.ref
                is KClass<*> -> {
                    if (kType.arguments.isEmpty())
                        DataTypeRefInternal.DefaultName(DefaultFqName.parse(checkNotNull(kClassifier.qualifiedName)))
                    else {
                        instantiateGenericOpaqueType(kType)
                    }
                }

                is KTypeParameter -> if (isAllowedTypeParameter(kClassifier))
                    typeVariableUsage(kClassifier).ref
                else
                    schemaBuildingFailure("Type parameter '$kClassifier' cannot be used as a type")

                else -> error("can't convert an unexpected type $kType to data type reference")
            }
        }

        private fun isAllowedTypeParameter(typeParameter: KTypeParameter): Boolean =
            currentContextStack.any { contextElement ->
                contextElement is SchemaBuildingContextElement.ModelMemberContextElement && contextElement.kCallable.typeParameters.any {
                    Workarounds.typeParameterMatches(it, typeParameter)
                }
            }

        private fun instantiateGenericOpaqueType(kType: KType): DataTypeRef {
            require(kType.arguments.isNotEmpty())
            val kClass = kType.classifier as KClass<*>

            val fqn = DefaultFqName.parse(checkNotNull(kClass.qualifiedName))

            val typeArguments = kType.arguments.map {
                if (it == KTypeProjection.STAR)
                    TypeArgumentInternal.DefaultStarProjection()
                else {
                    inContextOf(SchemaBuildingTags.typeArgument(it)) {
                        if (it.variance != KVariance.INVARIANT) {
                            schemaBuildingFailure("Illegal '${it.variance}' variance")
                        }
                        val argumentTypeRef = this.typeRef(
                            it.type ?: schemaBuildingFailure("Type argument has no proper type")
                        )
                        TypeArgumentInternal.DefaultConcreteTypeArgument(argumentTypeRef)
                    }
                }
            }

            val registeredTypeSignature = typeSignatures.getOrPut(fqn) {
                DataTypeInternal.DefaultParameterizedTypeSignature(
                    fqn,
                    kClass.typeParameters.map {
                        if (it.variance == KVariance.IN)
                            schemaBuildingFailure("Type parameter '$it' of '$kType' has 'in' variance, which is not supported")
                        DataTypeInternal.DefaultParameterizedTypeSignature.TypeParameter(it.name, it.variance == KVariance.OUT)
                    },
                    kClass.java.name
                )
            }

            val instance = registerTypeInstance(registeredTypeSignature, typeArguments)

            return DataTypeRefInternal.DefaultNameWithArgs(instance.name, instance.typeArguments)
        }

        private fun registerTypeInstance(
            registeredTypeSignature: ParameterizedTypeSignature,
            typeArguments: List<TypeArgument>
        ) = typeInstances.getOrPut(registeredTypeSignature.name) { mutableMapOf() }.getOrPut(typeArguments) {
            DataTypeInternal.DefaultParameterizedTypeInstance(registeredTypeSignature, typeArguments)
        }

        private fun typeVariableUsage(kTypeParameter: KTypeParameter): DataType.TypeVariableUsage =
            typeVariables.getOrPut(kTypeParameter) { DataTypeInternal.DefaultTypeVariableUsage(nextTypeVariableId.getAndIncrement()) }
    }

    fun schemaFromTypes(
        topLevelReceiver: KClass<*>,
        types: Iterable<KClass<*>>,
        externalFunctions: List<KFunction<*>> = emptyList(),
        externalObjects: Map<FqName, KClass<*>> = emptyMap(),
        defaultImports: List<FqName> = emptyList(),
    ): AnalysisSchema {
        val host = Host(topLevelReceiver)
        val preIndex = createPreIndex(host, types)

        val dataTypes = preIndex.types.filter { it.typeParameters.none() }.map {
            createDataType(host, it, preIndex)
        }

        val (infixExternalFunctions, regularExternalFunctions) = externalFunctions.partition { it.isInfix }
        val extFunctions = regularExternalFunctions.mapNotNull { functionExtractor.topLevelFunction(host, it, preIndex) }.associateBy { it.fqName }
        val infixFunctions = infixExternalFunctions.mapNotNull { functionExtractor.topLevelFunction(host, it, preIndex) }.associateBy { it.fqName }
        val extObjects = externalObjects.map { (key, value) ->
            host.withTag(SchemaBuildingTags.externalObject(key)) {
                key to DefaultExternalObjectProviderKey(host.containerTypeRef(value::class))
            }
        }.toMap()

        val topLevelReceiverName = topLevelReceiver.fqName

        val schema = DefaultAnalysisSchema(
            dataTypes.filterIsInstance<DataClass>().single { it.name == topLevelReceiverName },
            dataTypes.associateBy { it.name } + preIndex.syntheticTypes.associateBy { it.name },
            host.typeSignatures,
            host.typeInstances,
            extFunctions,
            infixFunctions,
            extObjects,
            defaultImports.toSet(),
            augmentationsProvider.augmentations(host)
        )

        validateSchema(host, schema)

        return schema
    }

    private fun validateSchema(host: Host, schema: AnalysisSchema) {
        val configurableTypes = collectReachableContainerTypes(schema)
        checkGenericTypeUsage(host, configurableTypes)
        checkAllTypesInScope(host, schema, configurableTypes)
    }

    private fun checkGenericTypeUsage(host: Host, configurableTypes: Iterable<DataClass>) {
        configurableTypes.forEach {
            val kClass = host.dataClassToKClass[it]
            if (kClass != null) {
                host.inContextOfModelClass(kClass) {
                    if (kClass.typeParameters.isNotEmpty()) {
                        host.schemaBuildingFailure("Container types must not have any type parameters. Illegal type parameters: ${kClass.typeParameters.joinToString()}")
                    }
                }
            }
        }
    }

    private fun checkAllTypesInScope(host: Host, schema: AnalysisSchema, configurableTypes: Set<DataClass>) {
        val typeRefContext = SchemaTypeRefContext(schema)

        fun checkTypeInScope(dataTypeRef: DataTypeRef) {
            if (typeRefContext.maybeResolveRef(dataTypeRef) == null) {
                host.schemaBuildingFailure("Type '$dataTypeRef' is not in the schema")
            }
        }

        fun validateFunction(function: SchemaFunction) {
            host.withTag(SchemaBuildingTags.returnValueType(function.returnValueType)) {
                checkTypeInScope(function.returnValueType)
            }
            function.parameters.forEach { parameter ->
                host.withTag(SchemaBuildingTags.schemaParameter(parameter)) {
                    checkTypeInScope(parameter.type)
                }
            }
            when (val semantics = function.semantics) {
                is FunctionSemantics.ConfigureSemantics -> {
                    host.withTag(SchemaBuildingTags.configuredType(semantics.configuredType)) {
                        checkTypeInScope(semantics.configuredType)
                    }
                }

                else -> Unit
            }
        }

        schema.externalFunctionsByFqName.values.forEach {
            host.withTag(SchemaBuildingTags.schemaFunction(it)) {
                validateFunction(it)
            }
        }

        configurableTypes.forEach { type ->
            host.withTag(SchemaBuildingTags.schemaClass(type)) {
                type.memberFunctions.forEach { function ->
                    host.withTag(SchemaBuildingTags.schemaFunction(function)) {
                        validateFunction(function)
                    }
                }
                type.constructors.forEach { constructor ->
                    host.withTag(SchemaBuildingTags.schemaTypeConstructor(constructor)) {
                        validateFunction(constructor)
                    }
                }
                type.properties.forEach { property ->
                    host.withTag(SchemaBuildingTags.schemaProperty(property)) {
                        checkTypeInScope(property.valueType)
                    }
                }
            }

        }
    }

    private fun collectReachableContainerTypes(schema: AnalysisSchema) = buildSet {
        val typeRefContext = SchemaTypeRefContext(schema)

        fun visit(configurableType: DataClass) {
            if (!add(configurableType))
                return

            configurableType.memberFunctions.forEach {
                val semantics = it.semantics
                if (semantics is FunctionSemantics.ConfigureSemantics) {
                    when (val configuredType = typeRefContext.maybeResolveRef(semantics.configuredType)) {
                        is DataClass -> visit(configurableType)
                        is ParameterizedTypeInstance -> {
                            val dataClass = schema.dataClassTypesByFqName[configuredType.name] as? DataClass
                            if (dataClass != null) {
                                visit(dataClass)
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        visit(schema.topLevelReceiverType)
    }


    private
    val KClass<*>.fqName
        get() = DefaultFqName.parse(qualifiedName!!)

    class PreIndex {
        private
        val properties = mutableMapOf<KClass<*>, MutableMap<String, DataProperty>>()

        private
        val propertyOriginalTypes = mutableMapOf<KClass<*>, MutableMap<String, SupportedTypeProjection.SupportedType>>()

        private
        val claimedFunctions = mutableMapOf<KClass<*>, MutableSet<KCallable<*>>>()

        private val mutableSyntheticTypes = mutableMapOf<String, DataClass>()

        fun getOrRegisterSyntheticType(id: String, produceType: () -> DataClass): DataClass =
            mutableSyntheticTypes.getOrPut(id, produceType)

        fun addType(kClass: KClass<*>) {
            properties.getOrPut(kClass) { mutableMapOf() }
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }
        }

        fun addProperty(kClass: KClass<*>, property: DataProperty, originalType: SupportedTypeProjection.SupportedType) {
            properties.getOrPut(kClass) { mutableMapOf() }[property.name] = property
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }[property.name] = originalType
        }

        // TODO replace with a generic schema member inclusion tracking mechanism
        fun claimFunction(kClass: KClass<*>, kFunction: KCallable<*>) {
            claimedFunctions.getOrPut(kClass) { mutableSetOf() }.add(kFunction)
        }

        val syntheticTypes: List<DataClass>
            get() = mutableSyntheticTypes.values.toList()

        val types: Iterable<KClass<*>>
            get() = properties.keys

        fun hasType(kClass: KClass<*>): Boolean = kClass in properties

        fun getAllProperties(kClass: KClass<*>): List<DataProperty> = properties[kClass]?.values.orEmpty().toList()

        fun getClaimedFunctions(kClass: KClass<*>): Set<KCallable<*>> = claimedFunctions[kClass].orEmpty()

        fun getProperty(kClass: KClass<*>, name: String) = properties[kClass]?.get(name)
    }

    @Suppress("NestedBlockDepth")
    private
    fun createPreIndex(host: SchemaBuildingHost, types: Iterable<KClass<*>>): PreIndex {
        val typeDiscoveryServices = object : TypeDiscovery.TypeDiscoveryServices {
            override val propertyExtractor: PropertyExtractor
                get() = this@DataSchemaBuilder.propertyExtractor
            override val host: SchemaBuildingHost
                get() = host
        }

        val allTypeDiscoveries: MutableSet<TypeDiscovery.DiscoveredClass> = mutableSetOf()

        val allTypesToVisit = buildSet {
            fun visit(type: KClass<*>) {
                if (add(type)) {
                    val discoveriesToVisitNext = typeDiscovery.getClassesToVisitFrom(typeDiscoveryServices, type)
                    allTypeDiscoveries.addAll(discoveriesToVisitNext)

                    discoveriesToVisitNext.forEach {
                        if (!it.isHidden) {
                            visit(it.kClass)
                        }
                    }
                }
            }
            types.forEach(::visit)
        }

        checkDiscoveredTypeForIllegalHiddenTypeUsages(host, allTypeDiscoveries)

        return PreIndex().apply {
            allTypesToVisit.forEach { type ->
                host.inContextOfModelClass(type) {
                    addType(type)
                    val properties = propertyExtractor.extractProperties(host, type)
                    properties.forEach {
                        it.claimedFunctions.forEach { f -> claimFunction(type, f) }
                        addProperty(
                            type,
                            DefaultDataProperty(it.name, it.returnType, it.propertyMode, it.hasDefaultValue, it.isHiddenInDefinition, it.isDirectAccessOnly),
                            it.originalReturnType
                        )
                    }
                }
            }
        }
    }

    private fun checkDiscoveredTypeForIllegalHiddenTypeUsages(
        host: SchemaBuildingHost,
        allDiscoveries: MutableSet<TypeDiscovery.DiscoveredClass>
    ) {
        allDiscoveries.groupBy { it.kClass }.forEach { (kClass, discoveries) ->
            if (!isIgnoredInVisibilityChecks(kClass) && discoveries.any { it.isHidden } && discoveries.any { !it.isHidden }) {
                host.schemaBuildingFailure(
                    "Type '${kClass.qualifiedName}' is a hidden type and cannot be directly used." +
                            "\n  Appears as hidden:\n" +
                            discoveries.filter { it.isHidden }
                                .flatMap { it.discoveryTags }
                                .joinToString("\n") { "    - ${discoveryTagDescription(it, kClass)}" } +
                            "\n  Illegal usages:\n" +
                            discoveries.filterNot { it.isHidden }
                                .flatMap { it.discoveryTags }
                                .filter { it !is Supertype || it.ofType != kClass } // Filter out the self appearance in the type hierarchy
                                .joinToString("\n") { "    - ${discoveryTagDescription(it, kClass)}" }
                )
            }
        }
    }

    /**
     * Some types are widely used and do not make sense to hide; however, a model might accidentally hide them in a type hierarchy.
     * Avoid reporting their usages in other types as errors.
     */
    private fun isIgnoredInVisibilityChecks(kClass: KClass<*>) = when (kClass) {
        Iterable::class, Collection::class, List::class, Map::class, Set::class,
        Any::class, Unit::class,
        String::class, Int::class, Boolean::class, Long::class, Double::class -> true
        else -> false
    }

    private fun discoveryTagDescription(tag: DiscoveryTag, inTypeHierarchyOf: KClass<*>): String = when (tag) {
        is ContainerElement -> "as the element of a container '${tag.containerMember}'"
        is PropertyType -> "as the property type of '${tag.kClass.qualifiedName}.${tag.propertyName}'"
        is Supertype -> if (tag.ofType == inTypeHierarchyOf && tag.isHidden) "type '${inTypeHierarchyOf.qualifiedName}' is annotated as hidden" else
            "in the supertypes of '${tag.ofType.qualifiedName}'"
        is UsedInMember -> "referenced from member '${tag.member}'"
        is Special -> tag.description
    }

    @Suppress("UNCHECKED_CAST")
    private
    fun createDataType(
        host: Host,
        kClass: KClass<*>,
        preIndex: PreIndex
    ): DataType.ClassDataType = host.inContextOfModelClass(kClass) {
        when {
            isEnum(kClass) -> {
                val entryNames = (kClass as KClass<Enum<*>>).java.enumConstants.map { it.name }
                DefaultEnumClass(kClass.fqName, kClass.java.name, entryNames)
            }

            else -> {
                val properties = preIndex.getAllProperties(kClass)
                val functions = functionExtractor.memberFunctions(host, kClass, preIndex).toList()
                DefaultDataClass(kClass.fqName, kClass.java.name, listOf(), supertypesOf(kClass), properties, functions, emptyList())
                    .also { host.dataClassToKClass[it] = kClass }
            }
        }
    }

    private
    fun isEnum(kClass: KClass<*>): Boolean {
        return kClass.supertypes.any { it.isSubtypeOf(typeOf<Enum<*>>()) }
    }

    private
    fun supertypesOf(kClass: KClass<*>): Set<FqName> = buildSet {
        fun visit(supertype: KType) {
            val classifier = supertype.classifier as? KClass<*> ?: error("a supertype is not represented by KClass: $supertype")
            if (add(classifier.fqName)) {
                classifier.supertypes.forEach(::visit)
            }
        }
        kClass.supertypes.forEach(::visit)
    }
}
