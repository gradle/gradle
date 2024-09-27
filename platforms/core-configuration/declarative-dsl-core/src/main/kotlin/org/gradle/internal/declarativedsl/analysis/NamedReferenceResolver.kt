package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.language.AccessChain
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.asChainOrNull


interface NamedReferenceResolver {
    fun doResolveNamedReferenceToObjectOrigin(
        analysisContext: AnalysisContext,
        namedReference: NamedReference,
        expectedType: DataTypeRef?
    ): ObjectOrigin?

    fun doResolveNamedReferenceToAssignable(
        analysisContext: AnalysisContext,
        namedReference: NamedReference
    ): PropertyReferenceResolution?
}


class NamedReferenceResolverImpl(
    private val expressionResolver: ExpressionResolver
) : NamedReferenceResolver {
    override fun doResolveNamedReferenceToObjectOrigin(
        analysisContext: AnalysisContext,
        namedReference: NamedReference,
        expectedType: DataTypeRef?
    ): ObjectOrigin? =
        analysisContext.doResolveNamedReferenceToObject(namedReference, expectedType)

    override fun doResolveNamedReferenceToAssignable(
        analysisContext: AnalysisContext,
        namedReference: NamedReference
    ): PropertyReferenceResolution? =
        analysisContext.doResolveNamedReferenceToAssignableReference(namedReference)

    private
    fun AnalysisContext.doResolveNamedReferenceToAssignableReference(
        namedReference: NamedReference
    ): PropertyReferenceResolution? {
        val candidates = sequence {
            runPropertyAccessResolution(
                namedReference,
                onLocalValue = { yield(AssignmentResolution.ReassignLocalVal(it.localValue)) },
                onProperty = { yield(AssignmentResolution.AssignProperty(PropertyReferenceResolution(it.receiver, it.property))) },
                onExternalObject = { yield(AssignmentResolution.ReassignExternal(it)) }
            )
        }

        return when (val firstMatch = candidates.firstOrNull()) {
            null -> return null
            is AssignmentResolution.ReassignLocalVal -> {
                errorCollector.collect(ResolutionError(namedReference, ErrorReason.ValReassignment(firstMatch.localValue)))
                null
            }

            is AssignmentResolution.ReassignExternal -> {
                errorCollector.collect(ResolutionError(namedReference, ErrorReason.ExternalReassignment(firstMatch.external)))
                null
            }

            is AssignmentResolution.AssignProperty -> firstMatch.propertyReference
        }?.also {
            checkPropertyAccessOnCurrentReceiver(it.property, it.receiverObject, namedReference)
        }
    }

    @Suppress("NestedBlockDepth")
    private
    fun AnalysisContext.doResolveNamedReferenceToObject(
        namedReference: NamedReference,
        expectedType: DataTypeRef?
    ): ObjectOrigin? {
        if (namedReference.receiver == null && expectedType != null) {
            val dataType = resolveRef(expectedType)
            if (dataType is EnumClass) {
                val matchingNamedReference = dataType.entryNames.firstOrNull { it ==  namedReference.name}
                if (matchingNamedReference != null) {
                    return ObjectOrigin.EnumConstantOrigin(dataType, namedReference)
                }
            }
        }

        val candidates: Sequence<ObjectOrigin> = sequence {
            runPropertyAccessResolution(
                namedReference = namedReference,
                onLocalValue = { yield(it) },
                onProperty = { yield(it) },
                onExternalObject = { yield(it) }
            )
        }
        candidates.firstOrNull().let { result ->
            if (result == null) {
                errorCollector.collect(ResolutionError(namedReference, ErrorReason.UnresolvedReference(namedReference)))
                return null
            } else {
                if (result is ObjectOrigin.PropertyReference) {
                    checkAccessOnCurrentReceiver(result)
                    if (result.property.isWriteOnly) {
                        errorCollector.collect(ResolutionError(result.originElement, ErrorReason.NonReadableProperty(result.property)))
                        return null
                    } else {
                        return result
                    }
                } else {
                    return result
                }
            }
        }
    }

    private
    fun AnalysisContext.checkAccessOnCurrentReceiver(
        reference: ObjectOrigin.PropertyReference
    ) {
        checkPropertyAccessOnCurrentReceiver(reference.property, reference.receiver, reference.originElement)
    }

    private
    fun AnalysisContext.checkPropertyAccessOnCurrentReceiver(property: DataProperty, receiver: ObjectOrigin, access: LanguageTreeElement) {
        if (property.isDirectAccessOnly) {
            checkAccessOnCurrentReceiver(receiver, access)
        }
    }

    private
    inline fun AnalysisContext.runPropertyAccessResolution(
        namedReference: NamedReference,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternalObject: (ObjectOrigin.External) -> Unit
    ) {
        when (namedReference.receiver) {
            null -> resolveUnqualifiedNamedReference(
                namedReference = namedReference,
                onLocalValue = onLocalValue,
                onProperty = onProperty,
                onExternal = onExternalObject
            )

            else -> doResolveQualifiedNamedReference(
                namedReference,
                onProperty = onProperty,
                onExternalObject = onExternalObject,
            )
        }
    }

    private
    inline fun AnalysisContext.doResolveQualifiedNamedReference(
        namedReference: NamedReference,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternalObject: (ObjectOrigin.External) -> Unit
    ) {
        require(namedReference.receiver != null) { "property access with explicit receiver expected" }

        val propertyName = namedReference.name

        expressionResolver.doResolveExpression(this, namedReference.receiver, null)?.let { receiverOrigin ->
            findDataProperty(getDataType(receiverOrigin), propertyName)?.let { property ->
                onProperty(ObjectOrigin.PropertyReference(receiverOrigin, property, namedReference))
            }
        }

        namedReference.asChainOrNull()?.let { chain ->
            schema.externalObjectsByFqName[chain.asFqName()]?.let { externalObject ->
                onExternalObject(ObjectOrigin.External(externalObject, namedReference))
            }
        }
    }

    private
    inline fun AnalysisContextView.resolveUnqualifiedNamedReference(
        namedReference: NamedReference,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternal: (ObjectOrigin.External) -> Unit
    ) {
        require(namedReference.receiver == null) { "name-only property access is expected" }

        lookupNamedValueInScopes(namedReference, onLocalValue, onProperty)

        if (namedReference.name in imports) {
            schema.externalObjectsByFqName[imports[namedReference.name]]?.let { external ->
                onExternal(ObjectOrigin.External(external, namedReference))
            }
        }
    }

    private
    inline fun AnalysisContextView.lookupNamedValueInScopes(
        namedReference: NamedReference,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit
    ) {
        currentScopes.asReversed().forEach { scope ->
            val x = scope.findLocalAsObjectOrigin(namedReference.name)
            x?.let(onLocalValue)

            val receiverType = getDataType(scope.receiver)
            val y = findDataProperty(receiverType, namedReference.name)
            y?.let { property ->
                val receiver = ObjectOrigin.ImplicitThisReceiver(scope.receiver, isCurrentScopeReceiver = scope === currentScopes.last())
                onProperty(ObjectOrigin.PropertyReference(receiver, property, namedReference))
            }
        }
    }

    private
    fun AnalysisScopeView.findLocalAsObjectOrigin(name: String): ObjectOrigin.FromLocalValue? {
        val local = findLocal(name) ?: return null
        val fromLocalValue = ObjectOrigin.FromLocalValue(local.localValue, local.assignment)
        return fromLocalValue
    }

    private
    fun findDataProperty(
        receiverType: DataType,
        name: String
    ): DataProperty? =
        if (receiverType is DataClass) {
            receiverType.properties.find { !it.isHiddenInDsl && it.name == name }
        } else {
            null
        }

    sealed interface AssignmentResolution {
        data class AssignProperty(val propertyReference: PropertyReferenceResolution) : AssignmentResolution
        data class ReassignLocalVal(val localValue: LocalValue) : AssignmentResolution
        data class ReassignExternal(val external: ObjectOrigin.External) : AssignmentResolution
    }
}


private
fun AccessChain.asFqName(): FqName = DefaultFqName(nameParts.dropLast(1).joinToString("."), nameParts.last())
