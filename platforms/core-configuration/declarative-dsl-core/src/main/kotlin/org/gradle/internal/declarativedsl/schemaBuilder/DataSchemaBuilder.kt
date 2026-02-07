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
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Supertype
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

    val typeFailures: Iterable<SchemaResult.Failure>
    fun recordTypeFailure(failure: SchemaResult.Failure)

    fun recordClaimedMember(kClass: KClass<*>, member: SupportedCallable)
    fun recordMemberWithFailure(kClass: KClass<*>, member: SupportedCallable, failure: SchemaResult.Failure)
    fun membersWithFailures(kClass: KClass<*>): Map<SupportedCallable, Iterable<SchemaResult.Failure>>

    fun isUnusedMember(kClass: KClass<*>, member: SupportedCallable): Boolean

    fun classMembers(kClass: KClass<*>): ClassMembersForSchema
    fun declarativeSupertypesHierarchy(kClass: KClass<*>): Iterable<MaybeDeclarativeClassInHierarchy>

    /**
     * Convert a [kClass] to a type that can be used as a DCL _container_ (a block receiver).
     *
     * This overload should be used for those special [KClass] appearances in the schema that are already guaranteed to be non-parameterized and non-nullable.
     * For other cases, use the [containerTypeRef] overload that takes a [KType].
     */
    fun containerTypeRef(kClass: KClass<*>): SchemaResult<DataTypeRef>

    /**
     * Convert a [kType] to a type that can be used as a DCL _container_ (a block receiver).
     * Validates that the type is a proper usage of a non-parameterized and non-nullable [KClass] (not a type parameter).
     */
    fun containerTypeRef(kType: KType): SchemaResult<DataTypeRef>

    /**
     * Convert a [kType] to a type that can be used as a DCL model value type reference.
     * Validates that the type is not nullable.
     */
    fun modelTypeRef(kType: KType): SchemaResult<DataTypeRef>

    /**
     * Convert a [varargType] (element type) of a vararg function parameter to a DCL vararg (array) type reference.
     */
    fun varargTypeRef(varargType: KType): SchemaResult<DataTypeRef>

    /**
     * Convert a [kType] to a DCL type reference without usage validation.
     * The caller must validate the [kType] before using this function or using the returned type reference in the schema.
     */
    fun typeRef(kType: KType): SchemaResult<DataTypeRef>

    fun enterSchemaBuildingContext(contextElement: SchemaBuildingContextElement)
    fun leaveSchemaBuildingContext(contextElement: SchemaBuildingContextElement)

    fun <T> inIsolatedContext(action: () -> T): T

    val context: List<SchemaBuildingContextElement>
}

inline fun <R> SchemaBuildingHost.inContextOfModelClass(kClass: KClass<*>, doBuildSchema: () -> R): R =
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
    fun containerElementType(supportedType: SupportedTypeProjection.SupportedType) = TagContextElement("container element type '${supportedType.toKType()}'")
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

class DefaultSchemaBuildingHost(override val topLevelReceiverClass: KClass<*>) : SchemaBuildingHost {

    val typeSignatures = mutableMapOf<FqName, ParameterizedTypeSignature>()
    val typeInstances = mutableMapOf<FqName, MutableMap<List<TypeArgument>, ParameterizedTypeInstance>>()

    private val typeVariables = mutableMapOf<KTypeParameter, DataType.TypeVariableUsage>()
    private var nextTypeVariableId = AtomicLong()

    private val currentContextStack = mutableListOf<SchemaBuildingContextElement>()

    override val context: List<SchemaBuildingContextElement>
        get() = currentContextStack

    private val classMembersCache = mutableMapOf<KClass<*>, ClassMembersForSchema>()
    private val declarativeSupertypesCache = mutableMapOf<KClass<*>, Iterable<MaybeDeclarativeClassInHierarchy>>()

    private val claimedMembers = mutableMapOf<KClass<*>, MutableSet<SupportedCallable>>()
    private val failedMembers = mutableMapOf<KClass<*>, MutableMap<SupportedCallable, MutableList<SchemaResult.Failure>>>()

    private val mutableTypeFailures = mutableListOf<SchemaResult.Failure>()

    override val typeFailures: Iterable<SchemaResult.Failure> get() = mutableTypeFailures.toList()

    override fun recordTypeFailure(failure: SchemaResult.Failure) {
        mutableTypeFailures += failure
    }

    override fun recordClaimedMember(kClass: KClass<*>, member: SupportedCallable) {
        claimedMembers.getOrPut(kClass) { mutableSetOf() }.add(member)
    }

    override fun recordMemberWithFailure(kClass: KClass<*>, member: SupportedCallable, failure: SchemaResult.Failure) {
        failedMembers.getOrPut(kClass) { mutableMapOf() }.getOrPut(member) { mutableListOf() }.add(failure)
    }

    override fun membersWithFailures(kClass: KClass<*>): Map<SupportedCallable, Iterable<SchemaResult.Failure>> =
        failedMembers[kClass] ?: emptyMap()

    override fun isUnusedMember(kClass: KClass<*>, member: SupportedCallable): Boolean =
        claimedMembers[kClass]?.contains(member) != true && failedMembers[kClass]?.contains(member) != true

    override fun classMembers(kClass: KClass<*>): ClassMembersForSchema =
        classMembersCache.getOrPut(kClass) {
            // This action might happen in the context of some other action, like in extracting functions. To ensure that the context stacks for the members are not affected
            // by the outer context, use a new context for this action
            inIsolatedContext {
                collectMembersForSchema(this, kClass)
            }
        }

    override fun declarativeSupertypesHierarchy(kClass: KClass<*>) =
    // This action might happen in the context of some other action, like in extracting functions. To ensure that the context stacks for the members are not affected
        // by the outer context, use a new context for this action
        inIsolatedContext {
            declarativeSupertypesCache.getOrPut(kClass) { collectDeclarativeSuperclassHierarchy(this, kClass) }
        }

    override fun containerTypeRef(kClass: KClass<*>): SchemaResult<DataTypeRef> = containerTypeRef(kClass.starProjectedType)

    override fun containerTypeRef(kType: KType): SchemaResult<DataTypeRef> {
        when (kType.classifier) {
            is KTypeParameter -> return schemaBuildingFailure(SchemaBuildingIssue.UnsupportedTypeParameterAsContainerType(kType))
            else -> Unit
        }

        if (kType.arguments.isNotEmpty()) {
            return schemaBuildingFailure(SchemaBuildingIssue.UnsupportedGenericContainerType(kType))
        }
        return modelTypeRef(kType)
    }

    override fun modelTypeRef(kType: KType): SchemaResult<DataTypeRef> = when {
        kType.isMarkedNullable -> schemaBuildingFailure(SchemaBuildingIssue.UnsupportedNullableType(kType))
        else -> typeRef(kType)
    }

    override fun varargTypeRef(varargType: KType): SchemaResult<DataTypeRef> {
        val varargTypeSignature = typeSignatures.getOrPut(DefaultVarargSignature.name) { DefaultVarargSignature }

        val elementTypeRef = withTag(varargType(varargType)) {
            when (varargType) {
                typeOf<IntArray>() -> schemaResult(DataTypeInternal.DefaultIntDataType.ref)
                typeOf<LongArray>() -> schemaResult(DataTypeInternal.DefaultLongDataType.ref)
                typeOf<BooleanArray>() -> schemaResult(DataTypeInternal.DefaultBooleanDataType.ref)
                else -> varargType.arguments.singleOrNull()?.type?.let(::modelTypeRef)
                    ?: schemaBuildingFailure(SchemaBuildingIssue.UnsupportedVarargType(varargType))
            }
        }


        return elementTypeRef.map { element ->
            registerTypeInstance(varargTypeSignature, listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(element))).ref
        }
    }

    override fun enterSchemaBuildingContext(contextElement: SchemaBuildingContextElement) {
        currentContextStack.add(contextElement)
    }

    override fun leaveSchemaBuildingContext(contextElement: SchemaBuildingContextElement) {
        currentContextStack.removeLast().also {
            check(it === contextElement) { "Schema building context mismatch: expected $contextElement on top, got $it" }
        }
    }

    override fun <T> inIsolatedContext(action: () -> T): T {
        val oldContext = currentContextStack.toList()
        currentContextStack.clear()
        try {
            return action()
        } finally {
            currentContextStack.clear()
            currentContextStack.addAll(oldContext)
        }
    }

    override fun typeRef(kType: KType): SchemaResult<DataTypeRef> {
        check(currentContextStack.isNotEmpty()) { "Cannot reference a type $kType outside of a context" }

        return when (val kClassifier = kType.classifier) {
            Unit::class -> schemaResult(DataTypeInternal.DefaultUnitType.ref)
            Int::class -> schemaResult(DataTypeInternal.DefaultIntDataType.ref)
            String::class -> schemaResult(DataTypeInternal.DefaultStringDataType.ref)
            Boolean::class -> schemaResult(DataTypeInternal.DefaultBooleanDataType.ref)
            Long::class -> schemaResult(DataTypeInternal.DefaultLongDataType.ref)
            is KClass<*> ->
                if (kType.arguments.isEmpty())
                    schemaResult(DataTypeRefInternal.DefaultName(DefaultFqName.parse(checkNotNull(kClassifier.qualifiedName))))
                else {
                    instantiateGenericOpaqueType(kType)
                }

            is KTypeParameter -> if (isAllowedTypeParameter(kClassifier))
                schemaResult(typeVariableUsage(kClassifier).ref)
            else
                schemaBuildingFailure(SchemaBuildingIssue.IllegalUsageOfTypeParameterBoundByClass(kType))

            else -> schemaBuildingError("can't convert an unexpected type $kType to data type reference")
        }
    }

    private fun isAllowedTypeParameter(typeParameter: KTypeParameter): Boolean =
        currentContextStack.any { contextElement ->
            contextElement is SchemaBuildingContextElement.ModelMemberContextElement && contextElement.kCallable.typeParameters.any {
                Workarounds.typeParameterMatches(it, typeParameter)
            }
        }

    private fun instantiateGenericOpaqueType(kType: KType): SchemaResult<DataTypeRef> {
        require(kType.arguments.isNotEmpty())
        val kClass = kType.classifier as KClass<*>

        val fqn = DefaultFqName.parse(checkNotNull(kClass.qualifiedName))

        val typeArguments = kType.arguments.map { arg ->
            if (arg == KTypeProjection.STAR)
                TypeArgumentInternal.DefaultStarProjection()
            else {
                inContextOf(SchemaBuildingTags.typeArgument(arg)) {
                    if (arg.variance != KVariance.INVARIANT) {
                        return schemaBuildingFailure(SchemaBuildingIssue.IllegalVarianceInParameterizedTypeUsage(kClass, arg.variance!!))
                    }
                    val argumentTypeRef = modelTypeRef(
                        arg.type ?: schemaBuildingError("Type argument has no proper type")
                    )
                    TypeArgumentInternal.DefaultConcreteTypeArgument(argumentTypeRef.orFailWith {
                        return it
                    })
                }
            }
        }

        val registeredTypeSignature = typeSignatures.getOrPut(fqn) {
            DataTypeInternal.DefaultParameterizedTypeSignature(
                fqn,
                kClass.typeParameters.map {
                    if (it.variance == KVariance.IN)
                        return schemaBuildingFailure(
                            SchemaBuildingIssue.IllegalVarianceInParameterizedTypeUsage(kClass, it.variance)
                        )
                    DataTypeInternal.DefaultParameterizedTypeSignature.TypeParameter(it.name, it.variance == KVariance.OUT)
                },
                kClass.java.name
            )
        }

        val instance = registerTypeInstance(registeredTypeSignature, typeArguments)

        return schemaResult(DataTypeRefInternal.DefaultNameWithArgs(instance.name, instance.typeArguments))
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



class DataSchemaBuilder(
    private val typeDiscovery: TypeDiscovery,
    private val propertyExtractor: PropertyExtractor,
    private val functionExtractor: FunctionExtractor,
    private val augmentationsProvider: AugmentationsProvider
) {
    fun schemaFromTypes(
        topLevelReceiver: KClass<*>,
        types: Iterable<KClass<*>>,
        externalFunctions: List<KFunction<*>> = emptyList(),
        externalObjects: Map<FqName, KClass<*>> = emptyMap(),
        defaultImports: List<FqName> = emptyList(),
        schemaBuildingFailureReporter: SchemaFailureReporter
    ): AnalysisSchema {
        val host = DefaultSchemaBuildingHost(topLevelReceiver)
        val preIndex = createPreIndex(host, types)

        val dataTypes = preIndex.types.filter { it.typeParameters.none() }.map {
            createDataType(host, it, preIndex)
        }

        val (infixExternalFunctions, regularExternalFunctions) = externalFunctions.partition { it.isInfix }

        @OptIn(LossySchemaBuildingOperation::class) // there are no user-defined top-level functions, we don't need error handling there
        val extFunctions = regularExternalFunctions.mapNotNull { functionExtractor.topLevelFunction(host, it, preIndex)?.orError() }.associateBy { it.fqName }

        @OptIn(LossySchemaBuildingOperation::class) // there are no user-defined top-level functions, we don't need error handling there
        val infixFunctions = infixExternalFunctions.mapNotNull { functionExtractor.topLevelFunction(host, it, preIndex)?.orError() }.associateBy { it.fqName }

        val extObjects = externalObjects.map { (key, value) ->
            host.withTag(SchemaBuildingTags.externalObject(key)) {
                @OptIn(LossySchemaBuildingOperation::class) // there are no user-defined external objects, we don't need error handling there
                key to DefaultExternalObjectProviderKey(host.containerTypeRef(value::class).orError())
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

        validateSchemaInvariants(host, schema)

        val allFailures = collectSchemaBuildingFailures(host, preIndex, schema)
        schemaBuildingFailureReporter.report(schema, allFailures)

        return schema
    }

    private fun collectSchemaBuildingFailures(
        host: SchemaBuildingHost,
        preIndex: PreIndex,
        schema: DefaultAnalysisSchema,
    ): List<SchemaResult.Failure> = buildList {
        addAll(host.typeFailures.distinct())

        addAll(checkDiscoveredTypeForIllegalHiddenTypeUsages(host, preIndex.allDiscoveredTypes))

        preIndex.types.forEach { type ->
            if (schema.dataClassTypesByFqName[type.fqName] == null) {
                // Then for some reason this type is not in the schema. For example, it is a parameterized supertype.
                // The reason should be reported (or handled) by other means.
                return@forEach
            }

            host.classMembers(type).run {
                membersBySupertype.values.flatten().forEach { member ->
                    if (member is ExtractionResult.Failure) {
                        add(member.failure)
                    }
                }
                declarativeMembers.forEach { potentiallyDeclarative ->
                    if (host.isUnusedMember(type, potentiallyDeclarative)) {
                        host.inContextOfModelClass(type) {
                            host.inContextOfModelMember(potentiallyDeclarative.kCallable) {
                                add(host.schemaBuildingFailure(SchemaBuildingIssue.UnrecognizedMember))
                            }
                        }
                    }
                }
            }

            host.membersWithFailures(type).forEach { (_, failures) ->
                addAll(failures)
            }
        }
    }

    private fun validateSchemaInvariants(host: SchemaBuildingHost, schema: AnalysisSchema) {
        checkAllTypesInScope(host, schema, collectReachableContainerTypes(schema))
    }

    private fun checkAllTypesInScope(host: SchemaBuildingHost, schema: AnalysisSchema, configurableTypes: Set<DataClass>) {
        val typeRefContext = SchemaTypeRefContext(schema)

        fun checkTypeInScope(dataTypeRef: DataTypeRef) {
            if (typeRefContext.maybeResolveRef(dataTypeRef) == null) {
                host.schemaBuildingError("Type '$dataTypeRef' is not in the schema")
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

    class PreIndex(
        val allDiscoveredTypes: Set<DiscoveredClass> = emptySet(),
    ) {
        private
        val properties = mutableMapOf<KClass<*>, MutableMap<String, DataProperty>>()

        private val mutableSyntheticTypes = mutableMapOf<String, DataClass>()

        fun getOrRegisterSyntheticType(id: String, produceType: () -> DataClass): DataClass =
            mutableSyntheticTypes.getOrPut(id, produceType)

        fun addType(kClass: KClass<*>) {
            properties.getOrPut(kClass) { mutableMapOf() }
        }

        fun addProperty(kClass: KClass<*>, property: DataProperty) {
            properties.getOrPut(kClass) { mutableMapOf() }[property.name] = property
        }

        val syntheticTypes: List<DataClass>
            get() = mutableSyntheticTypes.values.toList()

        val types: Iterable<KClass<*>>
            get() = properties.keys

        fun hasType(kClass: KClass<*>): Boolean = kClass in properties

        fun getAllProperties(kClass: KClass<*>): List<DataProperty> = properties[kClass]?.values.orEmpty().toList()

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

        val allTypeDiscoveries: MutableSet<DiscoveredClass> = mutableSetOf()

        val allTypesToVisit = buildSet {
            fun visit(type: KClass<*>) {
                if (add(type)) {
                    val discoveriesToVisitNext = typeDiscovery.getClassesToVisitFrom(typeDiscoveryServices, type)
                    allTypeDiscoveries.addAll(discoveriesToVisitNext.filterIsInstance<SchemaResult.Result<DiscoveredClass>>().map { it.result })

                    discoveriesToVisitNext.filterIsInstance<SchemaResult.Failure>().forEach(host::recordTypeFailure)

                    discoveriesToVisitNext.forEach {
                        if (it is SchemaResult.Result  && !it.result.isHidden) {
                            visit(it.result.kClass)
                        }
                    }
                }
            }
            types.forEach(::visit)
        }

        return PreIndex(allTypeDiscoveries).apply {
            allTypesToVisit.forEach { type ->
                host.inContextOfModelClass(type) {
                    addType(type)
                    val properties = propertyExtractor.extractProperties(host, type)
                    properties.forEach { result ->
                        when (result) {
                            is ExtractionResult.Extracted -> {
                                result.metadata.fromMembers.forEach { host.recordClaimedMember(type, it) }
                                addProperty(type, result.result)
                            }
                            is ExtractionResult.Failure -> {
                                result.metadata.fromMembers.forEach {
                                    host.recordMemberWithFailure(type, it, result.failure)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkDiscoveredTypeForIllegalHiddenTypeUsages(
        host: SchemaBuildingHost,
        allDiscoveries: Set<DiscoveredClass>
    ): List<SchemaResult.Failure> =
        allDiscoveries.groupBy { it.kClass }.entries.flatMap { (kClass, discoveries) ->
            if (!isIgnoredInVisibilityChecks(kClass) && discoveries.any { it.isHidden } && discoveries.any { !it.isHidden }) {
                val hiddenBecause = discoveries.filter { it.isHidden }
                    .flatMap { it.discoveryTags }

                val illegalUsages = discoveries.filterNot { it.isHidden }
                    .flatMap { it.discoveryTags }
                    .filter { it !is Supertype || it.ofType != kClass } // Filter out the self-appearance in the type hierarchy


                listOf(host.schemaBuildingFailure(SchemaBuildingIssue.HiddenTypeUsedInDeclaration(kClass, hiddenBecause, illegalUsages)))
            } else emptyList()
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

    @Suppress("UNCHECKED_CAST")
    private
    fun createDataType(
        host: SchemaBuildingHost,
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
                val functions = buildList {
                    val results = functionExtractor.memberFunctions(host, kClass, preIndex)
                    results.forEach { functionResult ->
                        when (functionResult) {
                            is ExtractionResult.Extracted -> {
                                add(functionResult.result)
                                functionResult.metadata.fromMembers.forEach { host.recordClaimedMember(kClass, it) }
                            }

                            is ExtractionResult.Failure -> {
                                functionResult.metadata.fromMembers.forEach { host.recordMemberWithFailure(kClass, it, functionResult.failure) }
                            }
                        }
                    }
                }
                DefaultDataClass(kClass.fqName, kClass.java.name, listOf(), supertypesOf(kClass), properties, functions, emptyList())
            }
        }
    }

    private
    fun isEnum(kClass: KClass<*>): Boolean {
        return kClass.supertypes.any { it.isSubtypeOf(typeOf<Enum<*>>()) }
    }

    // TODO: make sure this is based on the declarative type hierarchies and takes the visibility into account
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
