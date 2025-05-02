package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.LocalValue
import java.util.concurrent.atomic.AtomicLong


interface AnalysisScopeView {
    val receiver: ObjectOrigin.ReceiverOrigin
    val ownLocals: Map<String, LocalValueAssignment>
    val syntacticEnclosure: LanguageTreeElement

    fun findLocal(name: String): LocalValueAssignment?
}


data class LocalValueAssignment(val localValue: LocalValue, val assignment: ObjectOrigin)


class AnalysisScope(
    private val previousScopeView: AnalysisScopeView?,
    override val receiver: ObjectOrigin.ReceiverOrigin,
    override val syntacticEnclosure: LanguageTreeElement
) : AnalysisScopeView {
    private
    val ownLocalsByName = mutableMapOf<String, LocalValueAssignment>()

    override val ownLocals: Map<String, LocalValueAssignment>
        get() = ownLocalsByName

    override fun findLocal(name: String): LocalValueAssignment? =
        ownLocalsByName[name] ?: previousScopeView?.findLocal(name)

    fun declareLocal(
        localValue: LocalValue,
        assignedObjectOrigin: ObjectOrigin,
        reportError: ErrorCollector
    ) {
        val name = localValue.name
        if (name in ownLocalsByName) {
            reportError.collect(ResolutionError(localValue, ErrorReason.DuplicateLocalValue(name)))
        }
        ownLocalsByName[name] = LocalValueAssignment(localValue, assignedObjectOrigin)
    }
}


interface TypeRefContext {
    fun resolveRef(dataTypeRef: DataTypeRef): DataType
    fun maybeResolveRef(dataTypeRef: DataTypeRef): DataType?
}


interface AnalysisContextView : TypeRefContext {
    val schema: AnalysisSchema
    val imports: Map<String, FqName>
    val currentScopes: List<AnalysisScopeView>
    val assignments: List<AssignmentRecord>
}


class SchemaTypeRefContext(val schema: AnalysisSchema) : TypeRefContext {
    /**
     * "Reconstruction" here and below is the process of making sure that the implementation of the interface like [TypeArgument] or [DataTypeRef] used
     * as the map key is the _current implementation_, as opposed to a [java.lang.reflect.Proxy] provided by the TAPI implementing those interfaces.
     *
     * This is needed to ensure that the keys used in the map match the keys passed during lookup.
     * When the proxies are stored as keys in the map, they will not match proper instances constructed by the client code, including the resolver code.
     */
    private fun reconstructTypeArgument(typeArgument: TypeArgument): TypeArgument = when (typeArgument) {
        is TypeArgument.ConcreteTypeArgument -> TypeArgumentInternal.DefaultConcreteTypeArgument(reconstructTypeRef(typeArgument.type))
        is TypeArgument.StarProjection -> TypeArgumentInternal.DefaultStarProjection()
    }

    private fun reconstructTypeRef(dataTypeRef: DataTypeRef): DataTypeRef {
        return when (dataTypeRef) {
            is DataTypeRef.Name -> DataTypeRefInternal.DefaultName(dataTypeRef.fqName)
            is DataTypeRef.NameWithArgs -> DataTypeRefInternal.DefaultNameWithArgs(
                dataTypeRef.fqName,
                dataTypeRef.typeArguments.map { reconstructTypeArgument(it) }
            )
            is DataTypeRef.Type -> DataTypeRefInternal.DefaultType(dataTypeRef.dataType)
        }
    }

    private val typeInstances = mutableMapOf<Pair<FqName, List<TypeArgument>>, DataType.ParameterizedTypeInstance>()

    override fun maybeResolveRef(dataTypeRef: DataTypeRef): DataType? = when (dataTypeRef) {
        is DataTypeRef.Name ->
            schema.dataClassTypesByFqName[dataTypeRef.fqName]

        is DataTypeRef.NameWithArgs -> {
            val signature = schema.genericSignaturesByFqName[dataTypeRef.fqName]
            if (signature != null) {
                typeInstances.getOrPut(dataTypeRef.fqName to dataTypeRef.typeArguments) {
                    DataTypeInternal.DefaultParameterizedTypeInstance(signature, dataTypeRef.typeArguments.map(::reconstructTypeArgument))
                }
            } else null
        }

        is DataTypeRef.Type -> dataTypeRef.dataType
    }

    override fun resolveRef(dataTypeRef: DataTypeRef): DataType =
        maybeResolveRef(dataTypeRef)
            ?: interpretationFailure("cannot resolve a type reference to '$dataTypeRef'")
}


/**
 * Represents a unique operation within a particular generation.  The invocation id should be unique within a single
 * interpretation step, but not across generations (i.e. two operations in different generations may have the same
 * invocation id).  Operations in different generations with the same invocation id have no relationship to each
 * other except by coincidence.
 */
data class OperationId(val invocationId: Long, val generationId: OperationGenerationId): Comparable<OperationId> {
    override fun toString(): String = "${generationId.ordinal}:$invocationId"

    override fun compareTo(other: OperationId): Int = comparator.compare(this, other)

    companion object {
        private val comparator = compareBy<OperationId> { it.generationId.ordinal }.thenBy { it.invocationId }
    }
}


class AnalysisContext(
    override val schema: AnalysisSchema,
    override val imports: Map<String, FqName>,
    val errorCollector: ErrorCollector,
    private val generationId: OperationGenerationId
) : AnalysisContextView {

    // TODO: thread safety?
    private
    val mutableScopes = mutableListOf<AnalysisScope>()
    private
    val mutableAssignments = mutableListOf<AssignmentRecord>()
    private
    val nextInstant = AtomicLong(1)
    private
    val mutableAdditions = mutableListOf<DataAdditionRecord>()
    private
    val mutableNestedObjectAccess = mutableListOf<NestedObjectAccessRecord>()

    override val currentScopes: List<AnalysisScope>
        get() = mutableScopes

    override val assignments: List<AssignmentRecord>
        get() = mutableAssignments

    val additions: List<DataAdditionRecord>
        get() = mutableAdditions

    val nestedObjectAccess: List<NestedObjectAccessRecord>
        get() = mutableNestedObjectAccess

    private
    val typeRefContext = SchemaTypeRefContext(schema)

    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = typeRefContext.resolveRef(dataTypeRef)
    override fun maybeResolveRef(dataTypeRef: DataTypeRef): DataType? = typeRefContext.maybeResolveRef(dataTypeRef)

    fun enterScope(newScope: AnalysisScope) {
        mutableScopes.add(newScope)
    }

    fun recordAssignment(resolvedTarget: PropertyReferenceResolution, resolvedRhs: TypedOrigin, assignmentMethod: AssignmentMethod, originElement: LanguageTreeElement): AssignmentRecord {
        val result = AssignmentRecord(resolvedTarget, resolvedRhs.objectOrigin, nextCallId(), assignmentMethod, originElement)
        mutableAssignments.add(result)
        return result
    }

    fun recordAugmentingAssignment(
        resolvedTarget: PropertyReferenceResolution,
        resolvedResult: ObjectOrigin.AugmentationOrigin,
        originElement: LanguageTreeElement
    ): AssignmentRecord {
        val result = AssignmentRecord(resolvedTarget, resolvedResult, nextCallId(), AssignmentMethod.Augmentation, originElement)
        mutableAssignments.add(result)
        return result
    }

    fun recordAddition(container: ObjectOrigin, dataObject: ObjectOrigin) {
        mutableAdditions += DataAdditionRecord(container, dataObject, nextCallId())
    }

    fun recordNestedObjectAccess(container: ObjectOrigin, dataObject: ObjectOrigin.AccessAndConfigureReceiver) {
        mutableNestedObjectAccess += NestedObjectAccessRecord(container, dataObject, nextCallId())
    }

    fun nextCallId(): OperationId = OperationId(nextInstant.incrementAndGet(), generationId)

    fun leaveScope(scope: AnalysisScope) {
        check(mutableScopes.last() === scope)
        mutableScopes.removeLast()
    }
}
