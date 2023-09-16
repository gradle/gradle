package analysis

import com.h0tk3y.kotlin.staticObjectNotation.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.evaluation.DataValue
import java.util.concurrent.atomic.AtomicLong
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.full.isSubclassOf

data class ResolutionResult(
    val exprResolution: Map<Expr, ObjectOrigin>,
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
    data class DuplicateLocalValue(val name: String) : ErrorReason
    data object UnresolvedAssignmentLhs : ErrorReason // TODO: report candidate with rejection reasons
    data object UnresolvedAssignmentRhs : ErrorReason // TODO: resolution trace here, too?
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

class AnalysisContext(
    override val schema: AnalysisSchema,
    override val imports: Map<String, FqName>,
    val errorCollector: (ResolutionError) -> Unit
) : AnalysisContextView {

    // TODO: thread safety?
    private val mutableScopes = mutableListOf<AnalysisScope>()
    private val mutableAssignments = mutableMapOf<PropertyReferenceResolution, ObjectOrigin>()
    private val nextFunctionCallId = AtomicLong(0)
    private val mutableAdditions = mutableListOf<DataAddition>()

    override val currentScopes: List<AnalysisScope>
        get() = mutableScopes

    override val assignments: Map<PropertyReferenceResolution, ObjectOrigin>
        get() = mutableAssignments
    
    val additions: List<DataAddition> get() = mutableAdditions
    
    override fun resolveRef(dataTypeRef: DataTypeRef): DataType = when (dataTypeRef) {
        is DataTypeRef.Name -> schema.dataClassesByFqName.getValue(dataTypeRef.fqName)
        is DataTypeRef.Type -> dataTypeRef.type
    }

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
            return ResolutionResult(emptyMap(), assignments, additions, errors)
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

    fun AnalysisContext.analyzeCodeInProgramOrder(trees: List<LanguageTreeElement>) {
        for (tree in trees) {
            when (tree) {
                is Assignment -> doAnalyzeAssignment(tree)
                is FunctionCall -> doResolveFunctionCall(tree).also { result ->
                    if (result != null && result.function.semantics !is FunctionSemantics.ConfigureSemantics) {
                        errorCollector(ResolutionError(tree, ErrorReason.DanglingPureExpression))
                    }
                }

                is LocalValue -> doAnalyzeLocal(tree)
                is AccessChain, is Literal<*>, is Block -> errorCollector(
                    ResolutionError(
                        tree,
                        ErrorReason.DanglingPureExpression
                    )
                )

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
            currentScopes.last().declareLocal(localValue, rhs, errorCollector)
        }
    }

    fun AnalysisContext.doAnalyzeAssignment(assignment: Assignment) {
        val lhsResolution = tryResolveAssignmentLhsAccessChain(assignment.lhs)
        if (lhsResolution == null) {
            errorCollector(ResolutionError(assignment.lhs, ErrorReason.UnresolvedAssignmentLhs))
        } else {
            if (lhsResolution.property.isReadOnly) {
                errorCollector(ResolutionError(assignment.rhs, ErrorReason.ReadOnlyPropertyAssignment))
            } else {
                val rhsResolution = doResolveExpression(assignment.rhs)
                if (rhsResolution == null) {
                    errorCollector(ResolutionError(assignment.rhs, ErrorReason.UnresolvedAssignmentRhs))
                } else {
                    recordAssignment(lhsResolution, rhsResolution)
                }
            }
        }
    }

    fun AnalysisContext.doResolveExpression(expr: Expr): ObjectOrigin? = when (expr) {
        is AccessChain -> tryResolveExprAccessChainToObject(expr)
        is FunctionCall -> doResolveFunctionCall(expr)
        is Literal<*> -> literalObjectOrigin(expr)
    }

    fun <T> literalObjectOrigin(literalExpr: Literal<T>): ObjectOrigin =
        ObjectOrigin.ConstantOrigin(DataValue.Constant(literalExpr.type, literalExpr, literalExpr.value))

    fun AnalysisContextView.tryResolveExprAccessChainToObject(accessChain: AccessChain): ObjectOrigin? =
        tryResolveAccessChainToExternalObjectProperty(accessChain)?.asObjectOrigin(accessChain)
            ?: tryResolveAccessChainToLocalValueProperty(accessChain) ?: tryResolveAccessChainToReceiverProperty(
                accessChain
            )?.asObjectOrigin(accessChain)

    fun AnalysisContextView.tryResolveAssignmentLhsAccessChain(accessChain: AccessChain): PropertyReferenceResolution? =
        tryResolveAccessChainToExternalObjectProperty(accessChain) ?: tryResolveAccessChainToLhsViaLocalValue(
            accessChain
        ) ?: tryResolveAccessChainToReceiverProperty(accessChain)
    // TODO: report an error in assignments to a local value

    fun AnalysisContext.doResolveFunctionCall(functionCall: FunctionCall): ObjectOrigin.FromFunctionInvocation? {
        val receiver = if (functionCall.accessChain.length == 1) {
            currentScopes.last().receiver
        } else tryResolveExprAccessChainToObject(functionCall.accessChain.dropLast(1))

        val argResolutions = functionCall.args.filterIsInstance<FunctionArgument.ValueArgument>().associateWith {
            doResolveExpression(it.expr) ?: return@doResolveFunctionCall null
        }

        if (functionCall.args.count { it is FunctionArgument.Lambda } > 1) {
            // TODO: report functions with more than one lambda, those are not supported for now
            return null
        }

        if (receiver != null) {
            val receiverType = getDataType(receiver)
            if (receiverType !is DataType.DataClass<*>) {
                // TODO: extensions on non-class types?
                return null
            } else {
                val function: FunctionResolutionAndBinding? =
                    findMemberFunction(receiver, functionCall, argResolutions)
                        ?: findDataConstructor(functionCall, argResolutions)
                        ?: findTopLevelFunction(functionCall, argResolutions)

                if (function != null) {
                    val newFunctionCallId = nextFunctionCallId()
                    val valueBinding = function.binding.toValueBinding(argResolutions)
                    val result = ObjectOrigin.FromFunctionInvocation(
                        function.schemaFunction,
                        function.receiver,
                        valueBinding,
                        functionCall,
                        newFunctionCallId
                    )
                    
                    if (function.schemaFunction.semantics is FunctionSemantics.AddAndConfigure) {
                        recordAddition(receiver, result)
                    }

                    // we have chosen the function, now we go to the lambda if it's there
                    val maybeLambda = functionCall.args.filterIsInstance<FunctionArgument.Lambda>().singleOrNull()
                    if (maybeLambda != null) {
                        if (function.schemaFunction.semantics is FunctionSemantics.ConfigureSemantics) {
                            // TODO: avoid deep recursion?
                            withScope(AnalysisScope(currentScopes.last(), result, maybeLambda)) {
                                analyzeCodeInProgramOrder(maybeLambda.block.statements)
                            }
                        } else {
                            return null
                        }
                    }
                    
                    return result
                }
            }
        }
        // TODO: report error?
        return null
    }

    fun findDataConstructor(
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): FunctionResolutionAndBinding? {
        return null
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
    ): FunctionResolutionAndBinding? {
        val receiverType = getDataType(receiver) as? DataType.DataClass<*> 
            ?: return null
        val functionName = functionCall.accessChain.asFqName().simpleName
        val matchingMembers = receiverType.memberFunctions.filter { it.simpleName == functionName }
        // TODO: support optional parameters?
        // TODO: support at least minimal overload resolution?
        val args = functionCall.args

        // TODO: lambdas are handled in a special way and don't participate in signature matching now
        val signatureSizeMatches = preFilterSignatures(matchingMembers, args)
        if (signatureSizeMatches.isEmpty()) {
            return null
        }

        val matchingOverloads = chooseMatchingOverloads(receiver, signatureSizeMatches, args, argResolution)

        // TODO: report overload ambiguity?
        return matchingOverloads.singleOrNull()
    }

    private fun preFilterSignatures(
        matchingMembers: List<SchemaFunction>,
        args: List<FunctionArgument>,
    ) = matchingMembers.filter { it.parameters.size >= args.filterIsInstance<FunctionArgument.ValueArgument>().size }

    fun AnalysisContextView.findTopLevelFunction(
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): FunctionResolutionAndBinding? {
        val args = functionCall.args
        
        val candidates = buildList {
            val fqn = functionCall.accessChain.asFqName()
            schema.externalFunctionsByFqName[fqn]?.let { add(it) }
            
            if (functionCall.accessChain.length == 1) {
                val maybeImport = imports[fqn.simpleName]
                if (maybeImport != null) {
                    schema.externalFunctionsByFqName[maybeImport]?.let { add(it) }
                }
            }
        }
        
        val matchingOverloads = chooseMatchingOverloads(null, preFilterSignatures(candidates, args), args, argResolution)
        
        // TODO: report overload ambiguity?
        return matchingOverloads.singleOrNull()
    }

    private fun TypeRefContext.chooseMatchingOverloads(
        receiver: ObjectOrigin?,
        signatureSizeMatches: List<SchemaFunction>,
        args: List<FunctionArgument>,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> = signatureSizeMatches.mapNotNull { candidate ->
        // TODO: lambdas are omitted from this process, for now
        val binding = bindFunctionParametersToArguments(candidate.parameters, args.filterIsInstance<FunctionArgument.ValueArgument>())
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
    val lastPositionalArgIndex = arguments.indices.lastOrNull { arguments[it] is FunctionArgument.Positional } ?: arguments.size

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
}

fun AnalysisContextView.tryResolveAccessChainToLhsViaLocalValue(accessChain: AccessChain): PropertyReferenceResolution? {
    val local = resolveLocalValueAsObjectSource(accessChain.nameParts.first()) ?: return null
    return if (accessChain.length == 1) null
    else tryResolveAccessSuffixAsPropertyOnObject(local, accessChain, accessChain)
}

fun AnalysisContextView.tryResolveAccessChainToLocalValueProperty(accessChain: AccessChain): ObjectOrigin? {
    val local = resolveLocalValueAsObjectSource(accessChain.nameParts.first()) ?: return null
    return if (accessChain.length == 1) local
    else tryResolveAccessSuffixAsPropertyOnObject(local, accessChain, accessChain)?.asObjectOrigin(accessChain)
}

private fun AnalysisContextView.resolveLocalValueAsObjectSource(name: String): ObjectOrigin.FromLocalValue? {
    val scope = currentScopes.last() // TODO: resolve against the outer scopes, too
    val local = scope.findLocal(name) ?: return null
    val fromLocalValue = ObjectOrigin.FromLocalValue(local.localValue, local.assignment)
    return fromLocalValue
}

fun AnalysisContextView.tryResolveAccessChainToReceiverProperty(accessChain: AccessChain): PropertyReferenceResolution? {
    val scope = currentScopes.last() // TODO: probably resolve against the outer scopes, too
    return tryResolveAccessSuffixAsPropertyOnObject(
        scope.receiver, accessChain, accessChain
    )
}

// TODO: check with the spec, which candidate should win
fun AnalysisContextView.tryResolveAccessChainToExternalObjectProperty(accessChain: AccessChain): PropertyReferenceResolution? {
    val candidates = candidatesForExternalObjectAccess(accessChain)
    candidates.forEach { (prefixChain, obj) ->
        val afterPrefix = accessChain.afterPrefix(prefixChain)
        val resolved = tryResolveAccessSuffixAsPropertyOnObject(obj, afterPrefix, accessChain)
        if (resolved != null) return resolved
    }
    return null
}

private fun AnalysisContextView.candidatesForExternalObjectAccess(accessChain: AccessChain): List<Pair<AccessChain, ObjectOrigin>> =
    buildList {
        val simpleName = accessChain.nameParts.first()
        val importFqn = imports[simpleName]
        if (importFqn != null) {
            val maybeObject = schema.externalObjectsByFqName[importFqn]
            if (maybeObject != null) {
                add(accessChain.takeFirst(1) to ObjectOrigin.External(maybeObject, accessChain))
            }
        }
        addAll(resolvePossibleExternalObjectAccessByPrefix(accessChain))
    }

/**
 * Can return more than one object origin, if there is e.g. a package name `a.b.c` and an object `a.b`
 */
private fun AnalysisContextView.resolvePossibleExternalObjectAccessByPrefix(accessChain: AccessChain): List<Pair<AccessChain, ObjectOrigin>> {
    val result = buildList<Pair<AccessChain, ObjectOrigin>> {
        (1..<accessChain.nameParts.size).forEach { accessLength ->
            val prefixChain = accessChain.dropLast(accessLength)
            val maybeExternalObject = resolveAccessChainToExternalObjectByFqn(prefixChain)
            if (maybeExternalObject != null) {
                // TODO: more careful about the AST reference?
                add(prefixChain to ObjectOrigin.External(maybeExternalObject, accessChain))
            }
        }
    }
    return result
}

private fun AnalysisContextView.tryResolveAccessSuffixAsPropertyOnObject(
    objectOrigin: ObjectOrigin,
    accessSuffix: AccessChain,
    inAccessChain: AccessChain,
): PropertyReferenceResolution? {
    if (accessSuffix.nameParts.isEmpty()) return null

    var currentObject = objectOrigin
    accessSuffix.nameParts.dropLast(1).forEach { name ->
        val next = resolvePropertyOnObject(currentObject, inAccessChain, name) ?: return null
        currentObject = next
    }
    val property = findDataProperty(getDataType(currentObject), accessSuffix.nameParts.last())

    return if (property != null) {
        PropertyReferenceResolution(currentObject, property)
    } else null
}

private fun AnalysisContextView.resolvePropertyOnObject(
    objectOrigin: ObjectOrigin, inAccessChain: AccessChain, name: String
): ObjectOrigin? {
    val receiverType = getDataType(objectOrigin) ?: return null

    if (receiverType is DataType.DataClass<*>) {
        val property = findDataProperty(receiverType, name)
        if (property != null) {
            return ObjectOrigin.PropertyReference(objectOrigin, property, inAccessChain)
        }
    }

    return null
}

private fun findDataProperty(
    receiverType: DataType, name: String
): DataProperty? = if (receiverType is DataType.DataClass<*>) receiverType.properties.find { it.name == name } else null

fun PropertyReferenceResolution.asObjectOrigin(originElement: AccessChain): ObjectOrigin =
    ObjectOrigin.PropertyReference(this.receiverObject, property, originElement)

private fun AnalysisContextView.resolveAccessChainToExternalObjectByFqn(accessChain: AccessChain): ExternalObjectProviderKey? =
    schema.externalObjectsByFqName[accessChain.asFqName()]


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

private fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.ConstantOrigin -> objectOrigin.constant.type
    is ObjectOrigin.External -> objectOrigin.key.type
    is ObjectOrigin.FromFunctionInvocation -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.FromLocalValue -> getDataType(objectOrigin.assigned)
}