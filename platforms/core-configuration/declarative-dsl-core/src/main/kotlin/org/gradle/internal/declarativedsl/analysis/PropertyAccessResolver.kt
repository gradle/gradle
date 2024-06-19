package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.language.AccessChain
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.asChainOrNull


interface PropertyAccessResolver {
    fun doResolvePropertyAccessToObjectOrigin(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): ObjectOrigin?

    fun doResolvePropertyAccessToAssignable(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): PropertyReferenceResolution?
}


class PropertyAccessResolverImpl(
    private val expressionResolver: ExpressionResolver
) : PropertyAccessResolver {
    override fun doResolvePropertyAccessToObjectOrigin(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): ObjectOrigin? =
        analysisContext.doResolvePropertyAccessToObject(propertyAccess)

    override fun doResolvePropertyAccessToAssignable(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): PropertyReferenceResolution? =
        analysisContext.doResolvePropertyAccessToAssignableReference(propertyAccess)

    private
    fun AnalysisContext.doResolvePropertyAccessToAssignableReference(
        propertyAccess: PropertyAccess
    ): PropertyReferenceResolution? {
        val candidates = sequence {
            runPropertyAccessResolution(
                propertyAccess,
                onLocalValue = { yield(AssignmentResolution.ReassignLocalVal(it.localValue)) },
                onProperty = { yield(AssignmentResolution.AssignProperty(PropertyReferenceResolution(it.receiver, it.property))) },
                onExternalObject = { yield(AssignmentResolution.ReassignExternal(it)) }
            )
        }

        return when (val firstMatch = candidates.firstOrNull()) {
            null -> return null
            is AssignmentResolution.ReassignLocalVal -> {
                errorCollector.collect(ResolutionError(propertyAccess, ErrorReason.ValReassignment(firstMatch.localValue)))
                null
            }

            is AssignmentResolution.ReassignExternal -> {
                errorCollector.collect(ResolutionError(propertyAccess, ErrorReason.ExternalReassignment(firstMatch.external)))
                null
            }

            is AssignmentResolution.AssignProperty -> firstMatch.propertyReference
        }?.also {
            checkPropertyAccessOnCurrentReceiver(it.property, it.receiverObject, propertyAccess)
        }
    }

    private
    fun AnalysisContext.doResolvePropertyAccessToObject(
        propertyAccess: PropertyAccess
    ): ObjectOrigin? {
        val candidates: Sequence<ObjectOrigin> = sequence {
            runPropertyAccessResolution(
                propertyAccess = propertyAccess,
                onLocalValue = { yield(it) },
                onProperty = { yield(it) },
                onExternalObject = { yield(it) }
            )
        }
        candidates.firstOrNull().let { result ->
            if (result == null) {
                errorCollector.collect(ResolutionError(propertyAccess, ErrorReason.UnresolvedReference(propertyAccess)))
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
        propertyAccess: PropertyAccess,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternalObject: (ObjectOrigin.External) -> Unit
    ) {
        when (propertyAccess.receiver) {
            null -> resolveUnqualifiedPropertyAccess(
                propertyAccess = propertyAccess,
                onLocalValue = onLocalValue,
                onProperty = onProperty,
                onExternal = onExternalObject
            )

            else -> doResolveQualifiedPropertyAccess(
                propertyAccess,
                onProperty = onProperty,
                onExternalObject = onExternalObject,
            )
        }
    }

    private
    inline fun AnalysisContext.doResolveQualifiedPropertyAccess(
        propertyAccess: PropertyAccess,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternalObject: (ObjectOrigin.External) -> Unit
    ) {
        require(propertyAccess.receiver != null) { "Property access with explicit receiver expected" }

        val propertyName = propertyAccess.name

        expressionResolver.doResolveExpression(this, propertyAccess.receiver)?.let { receiverOrigin ->
            findDataProperty(getDataType(receiverOrigin), propertyName)?.let { property ->
                onProperty(ObjectOrigin.PropertyReference(receiverOrigin, property, propertyAccess))
            }
        }

        propertyAccess.asChainOrNull()?.let { chain ->
            schema.externalObjectsByFqName[chain.asFqName()]?.let { externalObject ->
                onExternalObject(ObjectOrigin.External(externalObject, propertyAccess))
            }
        }
    }

    private
    inline fun AnalysisContextView.resolveUnqualifiedPropertyAccess(
        propertyAccess: PropertyAccess,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit,
        onExternal: (ObjectOrigin.External) -> Unit
    ) {
        require(propertyAccess.receiver == null) { "Name-only property access is expected" }

        lookupNamedValueInScopes(propertyAccess, onLocalValue, onProperty)

        if (propertyAccess.name in imports) {
            schema.externalObjectsByFqName[imports[propertyAccess.name]]?.let { external ->
                onExternal(ObjectOrigin.External(external, propertyAccess))
            }
        }
    }

    private
    inline fun AnalysisContextView.lookupNamedValueInScopes(
        propertyAccess: PropertyAccess,
        onLocalValue: (ObjectOrigin.FromLocalValue) -> Unit,
        onProperty: (ObjectOrigin.PropertyReference) -> Unit
    ) {
        currentScopes.asReversed().forEach { scope ->
            scope.findLocalAsObjectOrigin(propertyAccess.name)
                ?.let(onLocalValue)

            findDataProperty(getDataType(scope.receiver), propertyAccess.name)?.let { property ->
                val receiver = ObjectOrigin.ImplicitThisReceiver(scope.receiver, isCurrentScopeReceiver = scope === currentScopes.last())
                onProperty(ObjectOrigin.PropertyReference(receiver, property, propertyAccess))
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
        if (receiverType is DataClass) receiverType.properties.find { !it.isHiddenInDsl && it.name == name } else null

    sealed interface AssignmentResolution {
        data class AssignProperty(val propertyReference: PropertyReferenceResolution) : AssignmentResolution
        data class ReassignLocalVal(val localValue: LocalValue) : AssignmentResolution
        data class ReassignExternal(val external: ObjectOrigin.External) : AssignmentResolution
    }
}


private
fun AccessChain.asFqName(): FqName = DefaultFqName(nameParts.dropLast(1).joinToString("."), nameParts.last())
