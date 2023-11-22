package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.language.LocalValue
import java.util.concurrent.atomic.AtomicLong

interface AnalysisScopeView {
    val receiver: ObjectOrigin
    val ownLocals: Map<String, LocalValueAssignment>
    val syntacticEnclosure: LanguageTreeElement

    fun findLocal(name: String): LocalValueAssignment?
}

data class LocalValueAssignment(val localValue: LocalValue, val assignment: ObjectOrigin)

class AnalysisScope(
    private val previousScopeView: AnalysisScopeView?,
    override val receiver: ObjectOrigin, override val syntacticEnclosure: LanguageTreeElement
) : AnalysisScopeView {
    private val ownLocalsByName = mutableMapOf<String, LocalValueAssignment>()

    override val ownLocals: Map<String, LocalValueAssignment>
        get() = ownLocalsByName

    override fun findLocal(name: String): LocalValueAssignment? =
        ownLocalsByName[name] ?: previousScopeView?.findLocal(name)

    fun declareLocal(
        localValue: LocalValue, assignedObjectOrigin: ObjectOrigin, reportError: (ResolutionError) -> Unit
    ) {
        val name = localValue.name
        if (name in ownLocalsByName) {
            reportError(ResolutionError(localValue, ErrorReason.DuplicateLocalValue(name)))
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
    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = when (dataTypeRef) {
        is DataTypeRef.Name -> schema.dataClassesByFqName.getValue(dataTypeRef.fqName)
        is DataTypeRef.Type -> dataTypeRef.type
    }
}

class AnalysisContext(
    override val schema: AnalysisSchema,
    override val imports: Map<String, FqName>,
    val errorCollector: (ResolutionError) -> Unit
) : AnalysisContextView {

    // TODO: thread safety?
    private val mutableScopes = mutableListOf<AnalysisScope>()
    private val mutableAssignments = mutableListOf<AssignmentRecord>()
    private val nextInstant = AtomicLong(1)
    private val mutableAdditions = mutableListOf<DataAddition>()

    override val currentScopes: List<AnalysisScope>
        get() = mutableScopes

    override val assignments: List<AssignmentRecord>
        get() = mutableAssignments

    val additions: List<DataAddition> get() = mutableAdditions

    private val typeRefContext = SchemaTypeRefContext(schema)

    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = typeRefContext.resolveRef(dataTypeRef)

    fun enterScope(newScope: AnalysisScope) {
        mutableScopes.add(newScope)
    }

    fun recordAssignment(resolvedTarget: PropertyReferenceResolution, resolvedRhs: ObjectOrigin, assignmentMethod: AssignmentMethod) {
        mutableAssignments.add(AssignmentRecord(resolvedTarget, resolvedRhs, nextInstant(), assignmentMethod))
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
