package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AssignmentResolution.AssignProperty
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AssignmentResolution.ReassignLocalVal
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataObjectResolverImpl.FunctionResolutionAndBinding
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin.FunctionInvocationOrigin
import com.h0tk3y.kotlin.staticObjectNotation.evaluation.DataValue
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.full.isSubclassOf

data class ResolutionResult(
    val topLevelReceiver: ObjectOrigin.TopLevelReceiver,
    val assignments: Map<PropertyReferenceResolution, ObjectOrigin>,
    val additions: List<DataAddition>,
    val errors: List<ResolutionError>
)

data class DataAddition(val container: ObjectOrigin, val dataObject: ObjectOrigin)

data class ResolutionError(
    val element: LanguageTreeElement,
    val errorReason: ErrorReason
)

sealed interface ErrorReason {
    data class AmbiguousImport(val fqName: FqName) : ErrorReason
    data class UnresolvedReference(val reference: Expr) : ErrorReason
    data class AmbiguousFunctions(val functions: List<FunctionResolutionAndBinding>) : ErrorReason
    data class ValReassignment(val localVal: LocalValue) : ErrorReason
    data class AssignmentTypeMismatch(val expected: DataType, val actual: DataType) : ErrorReason

    data object UnusedConfigureLambda : ErrorReason
    data class DuplicateLocalValue(val name: String) : ErrorReason
    data object UnresolvedAssignmentLhs : ErrorReason // TODO: report candidate with rejection reasons
    data object UnresolvedAssignmentRhs : ErrorReason // TODO: resolution trace here, too?
    data object UnitAssignment : ErrorReason
    data object ReadOnlyPropertyAssignment : ErrorReason
    data object DanglingPureExpression : ErrorReason
}

interface DataObjectResolver {
    fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult
}

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
    val assignments: Map<PropertyReferenceResolution, ObjectOrigin>
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
    private val mutableAssignments = mutableMapOf<PropertyReferenceResolution, ObjectOrigin>()
    private val nextFunctionCallId = AtomicLong(1)
    private val mutableAdditions = mutableListOf<DataAddition>()

    override val currentScopes: List<AnalysisScope>
        get() = mutableScopes

    override val assignments: Map<PropertyReferenceResolution, ObjectOrigin>
        get() = mutableAssignments

    val additions: List<DataAddition> get() = mutableAdditions
    
    val typeRefContext = SchemaTypeRefContext(schema)

    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = typeRefContext.resolveRef(dataTypeRef)

    fun enterScope(newScope: AnalysisScope) {
        mutableScopes.add(newScope)
    }

    fun recordAssignment(resolvedTarget: PropertyReferenceResolution, resolvedRhs: ObjectOrigin) {
        mutableAssignments[resolvedTarget] = resolvedRhs
    }

    fun recordAddition(container: ObjectOrigin, dataObject: ObjectOrigin) {
        mutableAdditions += DataAddition(container, dataObject)
    }

    fun nextFunctionCallId(): Long = nextFunctionCallId.incrementAndGet()

    fun leaveScope(scope: AnalysisScope) {
        check(mutableScopes.last() === scope)
        mutableScopes.removeLast()
    }
}

class DataObjectResolverImpl : DataObjectResolver {
    override fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult {
        val topLevelBlock = trees.singleOrNull { it is Block } as? Block
        require(trees.none { it is DataStatement } && topLevelBlock != null) { "expected a top-level block" }

        val errors = mutableListOf<ResolutionError>()
        val errorCollector: (ResolutionError) -> Unit = { errors.add(it) }

        val importContext = AnalysisContext(schema, emptyMap(), errorCollector)
        val importFqnBySimpleName = collectImports(
            trees.filterIsInstance<Import>(), importContext
        ) + schema.defaultImports.associateBy { it.simpleName }

        with(AnalysisContext(schema, importFqnBySimpleName, errorCollector)) {
            val topLevelReceiver = ObjectOrigin.TopLevelReceiver(schema.topLevelReceiverType, topLevelBlock)
            val topLevelScope = AnalysisScope(null, topLevelReceiver, topLevelBlock)
            withScope(topLevelScope) {
                analyzeCodeInProgramOrder(topLevelBlock.statements)
            }
            return ResolutionResult(topLevelReceiver, assignments, additions, errors)
        }
    }

    fun collectImports(
        trees: List<Import>,
        analysisContext: AnalysisContext
    ): Map<String, FqName> = buildMap {
        trees.forEach { import ->
            val fqn = FqName(
                import.name.nameParts.dropLast(1).joinToString("."), import.name.nameParts.last()
            )

            compute(fqn.simpleName) { _, existing ->
                if (existing != null && existing != fqn) {
                    analysisContext.errorCollector(ResolutionError(import, ErrorReason.AmbiguousImport(fqn)))
                    existing
                } else {
                    fqn
                }
            }
        }
    }

    // If we can trace the function invocation back to something that is not transient, we consider it not dangling
    fun isDanglingPureExpression(obj: FunctionInvocationOrigin): Boolean {
        fun isPotentiallyPersistentReceiver(objectOrigin: ObjectOrigin): Boolean = when (objectOrigin) {
            is ObjectOrigin.ConfigureReceiver -> true
            is ObjectOrigin.ConstantOrigin -> false
            is ObjectOrigin.External -> true
            is ObjectOrigin.NewObjectFromFunctionInvocation -> {
                val semantics = objectOrigin.function.semantics
                when (semantics) {
                    is FunctionSemantics.Builder -> error("should be impossible?")
                    is FunctionSemantics.AccessAndConfigure -> true
                    is FunctionSemantics.AddAndConfigure -> true
                    is FunctionSemantics.Pure -> false
                }
            }

            is ObjectOrigin.FromLocalValue -> true // TODO: also check for unused val?
            is ObjectOrigin.BuilderReturnedReceiver -> isPotentiallyPersistentReceiver(objectOrigin.receiverObject)
            is ObjectOrigin.NullObjectOrigin -> false
            is ObjectOrigin.PropertyReference -> true
            is ObjectOrigin.TopLevelReceiver -> true
            is ObjectOrigin.PropertyDefaultValue -> true
        }

        return when {
            obj.function.semantics is FunctionSemantics.Pure -> true
            obj is ObjectOrigin.BuilderReturnedReceiver -> !isPotentiallyPersistentReceiver(obj.receiverObject)
            else -> false
        }
    }

    fun AnalysisContext.analyzeCodeInProgramOrder(trees: List<LanguageTreeElement>) {
        for (tree in trees) {
            when (tree) {
                is Assignment -> doAnalyzeAssignment(tree)
                is FunctionCall -> doResolveFunctionCall(tree).also { result ->
                    if (result != null && isDanglingPureExpression(result))
                        errorCollector(ResolutionError(tree, ErrorReason.DanglingPureExpression))
                }

                is LocalValue -> doAnalyzeLocal(tree)
                is PropertyAccess, is Literal<*>, is Block -> errorCollector(
                    ResolutionError(
                        tree,
                        ErrorReason.DanglingPureExpression
                    )
                )

                is Expr -> {
                    errorCollector(ResolutionError(tree, ErrorReason.DanglingPureExpression))
                }

                is Import -> error("unexpected import in program code")
                is FunctionArgument -> error("function arguments should not appear in top-level trees")
            }
        }
    }

    fun AnalysisContext.doAnalyzeLocal(localValue: LocalValue) {
        val rhs = doResolveExpression(localValue.rhs)
        if (rhs == null) {
            errorCollector(ResolutionError(localValue, ErrorReason.UnresolvedAssignmentRhs))
        } else {
            if (getDataType(rhs) == DataType.UnitType) {
                errorCollector(ResolutionError(localValue, ErrorReason.UnitAssignment))
            }
            currentScopes.last().declareLocal(localValue, rhs, errorCollector)
        }
    }

    fun AnalysisContext.doAnalyzeAssignment(assignment: Assignment) {
        val lhsResolution =
            doResolvePropertyAccessToAssignableReference(assignment.lhs)
        if (lhsResolution == null) {
            errorCollector(ResolutionError(assignment.lhs, ErrorReason.UnresolvedAssignmentLhs))
        } else {
            var hasErrors = false
            if (lhsResolution.property.isReadOnly) {
                errorCollector(ResolutionError(assignment.rhs, ErrorReason.ReadOnlyPropertyAssignment))
                hasErrors = true
            }
            val rhsResolution = doResolveExpression(assignment.rhs)
            if (rhsResolution == null) {
                errorCollector(ResolutionError(assignment.rhs, ErrorReason.UnresolvedAssignmentRhs))
            } else {
                val rhsType = getDataType(rhsResolution)
                val lhsExpectedType = resolveRef(lhsResolution.property.type)
                if (rhsType == DataType.UnitType) {
                    errorCollector(ResolutionError(assignment, ErrorReason.UnitAssignment))
                    hasErrors = true
                }
                if (!checkIsAssignable(rhsType, lhsExpectedType)) {
                    errorCollector(
                        ResolutionError(assignment, ErrorReason.AssignmentTypeMismatch(lhsExpectedType, rhsType))
                    )
                    hasErrors = true
                }

                if (!hasErrors) {
                    recordAssignment(lhsResolution, rhsResolution)
                }
            }
        }
    }

    fun AnalysisContext.doResolveExpression(expr: Expr): ObjectOrigin? = when (expr) {
        is PropertyAccess -> doResolvePropertyAccessToObject(expr)
        is FunctionCall -> doResolveFunctionCall(expr)
        is Literal<*> -> literalObjectOrigin(expr)
        is Null -> ObjectOrigin.NullObjectOrigin(expr)
        is This -> currentScopes.last().receiver
    }

    fun <T : Any> literalObjectOrigin(literalExpr: Literal<T>): ObjectOrigin =
        ObjectOrigin.ConstantOrigin(DataValue.Constant(literalExpr.type, literalExpr, literalExpr.value))

    fun AnalysisContext.doResolvePropertyAccessToAssignableReference(propertyAccess: PropertyAccess): PropertyReferenceResolution? {
        val propertyName = propertyAccess.name
        val candidates: List<AssignmentResolution> = buildList {
            if (propertyAccess.receiver == null) {
                currentScopes.reversed().forEach { scope ->
                    val local = scope.resolveLocalValueAsObjectSource(propertyName)
                    if (local != null) {
                        add(ReassignLocalVal(local.localValue))
                    }
                    val scopeReceiver = scope.receiver
                    val property = findDataProperty(getDataType(scopeReceiver), propertyName)
                    if (property != null) {
                        add(AssignProperty(PropertyReferenceResolution(scopeReceiver, property)))
                    }
                }
                // TODO: support assignments to external properties?
            } else {
                val receiverOrigin = doResolveExpression(propertyAccess.receiver)
                if (receiverOrigin != null) {
                    val property = findDataProperty(getDataType(receiverOrigin), propertyName)
                    if (property != null) {
                        add(AssignProperty(PropertyReferenceResolution(receiverOrigin, property)))
                    }
                }
                // TODO: support assignments to external properties by FQN?
            }
        }
        return when (val firstMatch = candidates.firstOrNull()) {
            null -> return null
            is ReassignLocalVal -> {
                errorCollector(
                    ResolutionError(propertyAccess, ErrorReason.ValReassignment(firstMatch.localValue))
                )
                null
            }

            is AssignProperty -> firstMatch.propertyReference
        }
    }

    fun AnalysisContext.doResolvePropertyAccessToObject(propertyAccess: PropertyAccess): ObjectOrigin? {
        val propertyName = propertyAccess.name
        // TODO: optimize, resolve lazily against the receivers
        val candidates: List<ObjectOrigin> = buildList {
            if (propertyAccess.receiver == null) {
                // Simple name is resolved against the current receivers
                currentScopes.reversed().forEach { scope ->
                    val localValue = scope.resolveLocalValueAsObjectSource(propertyName)
                    if (localValue != null) {
                        add(localValue)
                    }
                    val scopeReceiver = scope.receiver
                    val property = findDataProperty(getDataType(scopeReceiver), propertyName)
                    if (property != null) {
                        add(ObjectOrigin.PropertyReference(scopeReceiver, property, propertyAccess))
                    }
                }
                addAll(lookupPropertiesInScopes(propertyName).map { it.asObjectOrigin(propertyAccess) })

                if (propertyName in imports) {
                    val externalObject = schema.externalObjectsByFqName[imports[propertyName]]
                    if (externalObject != null) {
                        add(ObjectOrigin.External(externalObject, propertyAccess))
                    }
                }
            } else {
                val receiverOrigin = doResolveExpression(propertyAccess.receiver)
                if (receiverOrigin != null) {
                    val property = findDataProperty(getDataType(receiverOrigin), propertyName)
                    if (property != null) {
                        add(ObjectOrigin.PropertyReference(receiverOrigin, property, propertyAccess))
                    }
                }
                val asChainOrNull = propertyAccess.asChainOrNull()
                if (asChainOrNull != null) {
                    val externalObject = schema.externalObjectsByFqName[asChainOrNull.asFqName()]
                    if (externalObject != null) {
                        add(ObjectOrigin.External(externalObject, propertyAccess))
                    }
                }
            }
        }
        return candidates.firstOrNull()
    }

    fun AnalysisContextView.lookupPropertiesInScopes(name: String): Sequence<PropertyReferenceResolution> =
        currentScopes.reversed().asSequence().mapNotNull { scope ->
            val receiver = scope.receiver
            val receiverType = getDataType(receiver)
            findDataProperty(receiverType, name)?.let { property -> PropertyReferenceResolution(receiver, property) }
        }

    fun AnalysisContext.doResolveFunctionCall(functionCall: FunctionCall): FunctionInvocationOrigin? {
        val argResolutions = functionCall.args.filterIsInstance<FunctionArgument.ValueArgument>()
            .associateWith {
                doResolveExpression(it.expr)
                // TODO report failure to resolve a function because of unresolved argument?
                    ?: return@doResolveFunctionCall null
            }

        if (functionCall.args.count { it is FunctionArgument.Lambda } > 1) {
            // TODO: report functions with more than one lambda, as those are not supported for now
            return null
        }

        // TODO: check the resolution order with the Kotlin spec
        val overloads: List<FunctionResolutionAndBinding> = buildList {
            if (functionCall.receiver == null) {
                currentScopes.reversed().forEach { scope ->
                    if (isEmpty()) {
                        addAll(findMemberFunction(scope.receiver, functionCall, argResolutions))
                    }
                }
            } else {
                val receiver = doResolveExpression(functionCall.receiver)
                if (receiver != null) {
                    addAll(findMemberFunction(receiver, functionCall, argResolutions))
                }
            }
            if (isEmpty()) {
                addAll(findDataConstructor(functionCall, argResolutions))
            }
            if (isEmpty()) {
                addAll(findTopLevelFunction(functionCall, argResolutions))
            }
        }

        return when (overloads.size) {
            0 -> {
                errorCollector(ResolutionError(functionCall, ErrorReason.UnresolvedReference(functionCall)))
                null
            }

            1 -> {
                val resolution = overloads.single()
                doProduceFunctionResult(resolution, argResolutions, functionCall, resolution.receiver)
            }

            else -> {
                errorCollector(ResolutionError(functionCall, ErrorReason.AmbiguousFunctions(overloads)))
                null
            }
        }
    }

    private fun AnalysisContext.doProduceFunctionResult(
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>,
        functionCall: FunctionCall,
        receiver: ObjectOrigin?
    ): FunctionInvocationOrigin {
        val newFunctionCallId = nextFunctionCallId()
        val valueBinding = function.binding.toValueBinding(argResolutions)
        val semantics = function.schemaFunction.semantics

        val result: FunctionInvocationOrigin = when (semantics) {
            is FunctionSemantics.Builder -> ObjectOrigin.BuilderReturnedReceiver(
                function.schemaFunction,
                checkNotNull(function.receiver),
                functionCall,
                valueBinding,
                newFunctionCallId
            )
            
            is FunctionSemantics.AccessAndConfigure -> when (semantics.returnType) {
                FunctionSemantics.AccessAndConfigure.ReturnType.UNIT -> 
                    newObjectInvocationResult(function, valueBinding, functionCall, newFunctionCallId)
                FunctionSemantics.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT ->
                    configureReceiverObject(semantics, function, functionCall)
            }

            else -> newObjectInvocationResult(function, valueBinding, functionCall, newFunctionCallId)
        }

        if (semantics is FunctionSemantics.AddAndConfigure) {
            require(receiver != null)

            recordAddition(receiver, result)
        }
        if (semantics is FunctionSemantics.Builder) {
            require(receiver != null)

            val parameter = function.schemaFunction.parameters.singleOrNull()
                ?: error("builder functions must have a single parameter")
            parameter.semantics as? ParameterSemantics.StoreValueInProperty
                ?: error("a builder function must assign its parameter to a property")
        }
        function.binding.binding.forEach { (param, arg) ->
            val paramSemantics = param.semantics
            if (paramSemantics is ParameterSemantics.StoreValueInProperty) {
                val property = paramSemantics.dataProperty
                recordAssignment(PropertyReferenceResolution(result, property), argResolutions.getValue(arg))
            }
        }

        // we have chosen the function and got its invocation result, now we go to the "configure" lambda if it's there
        val maybeLambda = functionCall.args.filterIsInstance<FunctionArgument.Lambda>().singleOrNull()
        if (maybeLambda != null) {
            if (semantics is FunctionSemantics.ConfigureSemantics) {
                val configureReceiver = when (semantics) {
                    is FunctionSemantics.AccessAndConfigure -> configureReceiverObject(semantics, function, functionCall)
                    is FunctionSemantics.AddAndConfigure -> result
                }
                // TODO: avoid deep recursion?
                withScope(AnalysisScope(currentScopes.last(), configureReceiver, maybeLambda)) {
                    analyzeCodeInProgramOrder(maybeLambda.block.statements)
                }
            } else {
                errorCollector(ResolutionError(functionCall, ErrorReason.UnusedConfigureLambda))
            }
        }
        return result
    }

    private fun configureReceiverObject(
        semantics: FunctionSemantics.AccessAndConfigure,
        function: FunctionResolutionAndBinding,
        functionCall: FunctionCall
    ) = when (val accessor = semantics.accessor) {
        is ConfigureAccessor.Property -> {
            require(function.receiver != null)
            ObjectOrigin.ConfigureReceiver(function.receiver, function.schemaFunction, functionCall, accessor)
        }
    }

    private fun newObjectInvocationResult(
        function: FunctionResolutionAndBinding,
        valueBinding: ParameterValueBinding,
        functionCall: FunctionCall,
        newFunctionCallId: Long
    ) = ObjectOrigin.NewObjectFromFunctionInvocation(
        function.schemaFunction,
        function.receiver,
        valueBinding,
        functionCall,
        newFunctionCallId
    )

    fun AnalysisContextView.findDataConstructor(
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> {
        // TODO: no nested types for now
        val candidateTypes = buildList<DataType> {
            val receiverAsChain = functionCall.receiver?.asChainOrNull()
            if (receiverAsChain != null) {
                val fqn = FqName(receiverAsChain.nameParts.joinToString("."), functionCall.name)
                val typeByFqn = schema.dataClassesByFqName[fqn]
                if (typeByFqn != null) {
                    add(typeByFqn)
                }
            } else if (functionCall.receiver == null) {
                val importedName = imports[functionCall.name]
                if (importedName != null) {
                    val maybeType = schema.dataClassesByFqName[importedName]
                    if (maybeType != null) {
                        add(maybeType)
                    }
                }
            }
        }
        val constructors = candidateTypes
            .flatMap { (it as? DataType.DataClass<*>)?.constructors.orEmpty() }
            .filter { it.parameters.size == functionCall.args.size }

        return chooseMatchingOverloads(null, constructors, functionCall.args, argResolution)
    }

    data class FunctionResolutionAndBinding(
        val receiver: ObjectOrigin?,
        val schemaFunction: SchemaFunction,
        val binding: ParameterArgumentBinding
    )

    fun TypeRefContext.findMemberFunction(
        receiver: ObjectOrigin,
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> {
        val receiverType = getDataType(receiver) as? DataType.DataClass<*>
            ?: return emptyList()
        val functionName = functionCall.name
        val matchingMembers = receiverType.memberFunctions.filter { it.simpleName == functionName }
        // TODO: support optional parameters?
        // TODO: support at least minimal overload resolution?
        val args = functionCall.args

        // TODO: lambdas are handled in a special way and don't participate in signature matching now
        val signatureSizeMatches = preFilterSignatures(matchingMembers, args)

        return chooseMatchingOverloads(receiver, signatureSizeMatches, args, argResolution)
    }

    private fun preFilterSignatures(
        matchingMembers: List<SchemaFunction>,
        args: List<FunctionArgument>,
    ) = matchingMembers.filter { it.parameters.size >= args.filterIsInstance<FunctionArgument.ValueArgument>().size }

    fun AnalysisContextView.findTopLevelFunction(
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> {
        val args = functionCall.args
        val receiver = functionCall.receiver

        // TODO: extension functions are not supported now; so it's either an FQN function reference or imported one
        if (receiver is PropertyAccess && receiver.asChainOrNull() != null || receiver == null) {
            val packageNameParts = (receiver as? PropertyAccess)?.asChainOrNull()?.nameParts.orEmpty()
            val candidates = buildList {
                val fqn = FqName(packageNameParts.joinToString("."), functionCall.name)
                schema.externalFunctionsByFqName[fqn]?.let { add(it) }

                if (receiver == null) {
                    val maybeImport = imports[fqn.simpleName]
                    if (maybeImport != null) {
                        schema.externalFunctionsByFqName[maybeImport]?.let { add(it) }
                    }
                }
            }

            val matchingOverloads =
                chooseMatchingOverloads(null, preFilterSignatures(candidates, args), args, argResolution)

            // TODO: report overload ambiguity?
            return matchingOverloads
        } else {
            return emptyList()
        }
    }

    private fun TypeRefContext.chooseMatchingOverloads(
        receiver: ObjectOrigin?,
        signatureSizeMatches: List<SchemaFunction>,
        args: List<FunctionArgument>,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> = signatureSizeMatches.mapNotNull { candidate ->
        // TODO: lambdas are omitted from this process, for now
        val binding = bindFunctionParametersToArguments(
            candidate.parameters,
            args.filterIsInstance<FunctionArgument.ValueArgument>()
        )
            ?: return@mapNotNull null
        if (!typeCheckFunctionCall(binding, argResolution)) {
            // TODO: return type mismatch in args
            return@mapNotNull null
        }
        FunctionResolutionAndBinding(receiver, candidate, binding)
    }
}

// TODO: performance optimization (?) Don't create the binding objects until a single candidate has been chosen
fun bindFunctionParametersToArguments(
    parameters: List<DataParameter>,
    arguments: List<FunctionArgument>,
): ParameterArgumentBinding? {
    fun findParameterByName(name: String): DataParameter? = parameters.find { it.name == name }
    val lastPositionalArgIndex =
        arguments.indices.lastOrNull { arguments[it] is FunctionArgument.Positional } ?: arguments.size

    val bindingMap = mutableMapOf<DataParameter, FunctionArgument.ValueArgument>()
    arguments.forEachIndexed { argIndex, arg ->
        if (argIndex < lastPositionalArgIndex && arg is FunctionArgument.Named && arg.name != parameters[argIndex].name) {
            // TODO: report mixed positional and named arguments?
            return@bindFunctionParametersToArguments null
        }

        if (arg is FunctionArgument.Named && parameters.none { it.name == arg.name }) {
            // TODO: report non-matching candidate?
            return@bindFunctionParametersToArguments null
        }

        val param =
            if (arg is FunctionArgument.Named)
                findParameterByName(arg.name)
                // TODO return a named argument that does not match any parameter
                    ?: return null
            else parameters[argIndex]

        if (param in bindingMap) {
            // TODO: report arg conflict
            return@bindFunctionParametersToArguments null
        }

        if (arg is FunctionArgument.ValueArgument) {
            bindingMap[param] = arg
        }
    }

    return if (parameters.all { it.isDefault || it in bindingMap }) {
        ParameterArgumentBinding(bindingMap)
    } else {
        null
    }
}

data class ParameterArgumentBinding(
    val binding: Map<DataParameter, FunctionArgument.ValueArgument>
)

fun ParameterArgumentBinding.toValueBinding(argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>) =
    ParameterValueBinding(binding.mapValues { (_, arg) -> argResolution.getValue(arg) })

fun TypeRefContext.typeCheckFunctionCall(
    binding: ParameterArgumentBinding,
    argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
): Boolean = binding.binding.all { (param, arg) ->
    checkIsAssignable(
        getDataType(argResolution.getValue(arg)),
        resolveRef(param.type)
    )
}

fun checkIsAssignable(valueType: DataType, isAssignableTo: DataType): Boolean = when (isAssignableTo) {
    is DataType.ConstantType<*> -> valueType == isAssignableTo
    is DataType.DataClass<*> -> valueType is DataType.DataClass<*> && valueType.kClass.isSubclassOf(isAssignableTo.kClass)
    DataType.NullType -> false // TODO: proper null type support
    DataType.UnitType -> valueType == DataType.UnitType
}

private fun AnalysisScopeView.resolveLocalValueAsObjectSource(name: String): ObjectOrigin.FromLocalValue? {
    val local = findLocal(name) ?: return null
    val fromLocalValue = ObjectOrigin.FromLocalValue(local.localValue, local.assignment)
    return fromLocalValue
}

private fun findDataProperty(
    receiverType: DataType, name: String
): DataProperty? =
    if (receiverType is DataType.DataClass<*>) receiverType.properties.find { it.name == name } else null

fun PropertyReferenceResolution.asObjectOrigin(originElement: PropertyAccess): ObjectOrigin =
    ObjectOrigin.PropertyReference(this.receiverObject, property, originElement)

@OptIn(ExperimentalContracts::class)
inline fun AnalysisContext.withScope(scope: AnalysisScope, action: () -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    enterScope(scope)
    try {
        action()
    } finally {
        leaveScope(scope)
    }
}

private val AccessChain.length: Int get() = nameParts.size

// TODO: handle the AST with more care?
private fun AccessChain.dropLast(nParts: Int) = AccessChain(nameParts.dropLast(nParts), originAst)
private fun AccessChain.takeFirst(nParts: Int) = AccessChain(nameParts.take(nParts), originAst)
private fun AccessChain.takeLast(nParts: Int) = AccessChain(nameParts.takeLast(nParts), originAst)
private fun AccessChain.afterPrefix(prefix: AccessChain): AccessChain {
    require(this.originAst === prefix.originAst)
    require(nameParts.take(prefix.nameParts.size) == prefix.nameParts)
    return AccessChain(nameParts.drop(nameParts.size), originAst)
}

private fun AccessChain.asFqName(): FqName = FqName(nameParts.dropLast(1).joinToString("."), nameParts.last())

fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.ConstantOrigin -> objectOrigin.constant.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.type)
    is ObjectOrigin.NewObjectFromFunctionInvocation -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.FromLocalValue -> getDataType(objectOrigin.assigned)
    is ObjectOrigin.NullObjectOrigin -> DataType.NullType
    is ObjectOrigin.ConfigureReceiver -> resolveRef(objectOrigin.accessor.objectType)
    is ObjectOrigin.BuilderReturnedReceiver -> getDataType(objectOrigin.receiverObject)
}
