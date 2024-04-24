package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
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
}


interface AnalysisContextView : TypeRefContext {
    val schema: AnalysisSchema
    val imports: Map<String, FqName>
    val currentScopes: List<AnalysisScopeView>
    val assignments: List<AssignmentRecord>
}


class SchemaTypeRefContext(val schema: AnalysisSchema) : TypeRefContext {
    override fun resolveRef(dataTypeRef: DataTypeRef): DataType =
        when (dataTypeRef) {
            is DataTypeRef.Name -> schema.dataClassesByFqName.getValue(dataTypeRef.getFqName()) as DefaultDataClass
            is DataTypeRef.Type -> dataTypeRef.getDataType()
        }
}


class AnalysisContext(
    override val schema: AnalysisSchema,
    override val imports: Map<String, FqName>,
    val errorCollector: ErrorCollector
) : AnalysisContextView {

    // TODO: thread safety?
    private
    val mutableScopes = mutableListOf<AnalysisScope>()
    private
    val mutableAssignments = mutableListOf<AssignmentRecord>()
    private
    val nextInstant = AtomicLong(1)
    private
    val mutableAdditions = mutableListOf<DataAddition>()

    override val currentScopes: List<AnalysisScope>
        get() = mutableScopes

    override val assignments: List<AssignmentRecord>
        get() = mutableAssignments

    val additions: List<DataAddition>
        get() = mutableAdditions

    private
    val typeRefContext = SchemaTypeRefContext(schema)

    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = typeRefContext.resolveRef(dataTypeRef)

    fun enterScope(newScope: AnalysisScope) {
        mutableScopes.add(newScope)
    }

    fun recordAssignment(resolvedTarget: PropertyReferenceResolution, resolvedRhs: ObjectOrigin, assignmentMethod: AssignmentMethod, originElement: LanguageTreeElement): AssignmentRecord {
        val result = AssignmentRecord(resolvedTarget, resolvedRhs, nextInstant(), assignmentMethod, originElement)
        mutableAssignments.add(result)
        return result
    }

    fun recordAddition(container: ObjectOrigin, dataObject: ObjectOrigin) {
        mutableAdditions += DataAddition(container, dataObject)
    }

    fun nextInstant(): Long = nextInstant.incrementAndGet()

    fun leaveScope(scope: AnalysisScope) {
        check(mutableScopes.last() === scope)
        mutableScopes.removeLast()
    }
}
