package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionCallResolver.FunctionResolutionAndBinding
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionArgument
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.language.PropertyAccess
import com.h0tk3y.kotlin.staticObjectNotation.language.asChainOrNull

interface FunctionCallResolver {
    fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall
    ): ObjectOrigin.FunctionInvocationOrigin?

    data class FunctionResolutionAndBinding(
        val receiver: ObjectOrigin?,
        val schemaFunction: SchemaFunction,
        val binding: ParameterArgumentBinding
    )
}

data class ParameterArgumentBinding(
    val binding: Map<DataParameter, FunctionArgument.ValueArgument>
)

class FunctionCallResolverImpl(
    private val expressionResolver: ExpressionResolver,
    private val codeAnalyzer: CodeAnalyzer
) : FunctionCallResolver {
    
    override fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall
    ): ObjectOrigin.FunctionInvocationOrigin? = with(context) {
        val argResolutions = functionCall.args.filterIsInstance<FunctionArgument.ValueArgument>()
            .associateWith {
                expressionResolver.doResolveExpression(this, it.expr)
                // TODO report failure to resolve a function because of unresolved argument?
                    ?: return@doResolveFunctionCall null
            }

        if (functionCall.args.count { it is FunctionArgument.Lambda } > 1) {
            // TODO: report functions with more than one lambda, as those are not supported for now
            return null
        }

        val overloads: List<FunctionResolutionAndBinding> = lookupFunctions(functionCall, argResolutions, context)

        return invokeIfSingleOverload(overloads, functionCall, argResolutions)
    }

    private fun AnalysisContext.invokeIfSingleOverload(
        overloads: List<FunctionResolutionAndBinding>,
        functionCall: FunctionCall,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ) = when (overloads.size) {
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

    // TODO: check the resolution order with the Kotlin spec
    private fun AnalysisContext.lookupFunctions(
        functionCall: FunctionCall,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>,
        context: AnalysisContext
    ): List<FunctionResolutionAndBinding> {
        val overloads: List<FunctionResolutionAndBinding> = buildList {
            if (functionCall.receiver == null) {
                currentScopes.reversed().forEach { scope ->
                    if (isEmpty()) {
                        addAll(findMemberFunction(scope.receiver, functionCall, argResolutions))
                    }
                }
            } else {
                val receiver = expressionResolver.doResolveExpression(context, functionCall.receiver)
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
        return overloads
    }

    private fun AnalysisContext.doProduceFunctionResult(
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>,
        functionCall: FunctionCall,
        receiver: ObjectOrigin?
    ): ObjectOrigin.FunctionInvocationOrigin {
        val newFunctionCallId = nextFunctionCallId()
        val valueBinding = function.binding.toValueBinding(argResolutions)
        val semantics = function.schemaFunction.semantics

        val result: ObjectOrigin.FunctionInvocationOrigin = when (semantics) {
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
                    is FunctionSemantics.AccessAndConfigure -> configureReceiverObject(
                        semantics,
                        function,
                        functionCall
                    )

                    is FunctionSemantics.AddAndConfigure -> result
                }
                // TODO: avoid deep recursion?
                withScope(AnalysisScope(currentScopes.last(), configureReceiver, maybeLambda)) {
                    codeAnalyzer.analyzeCodeInProgramOrder(this, maybeLambda.block.statements)
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

    private fun preFilterSignatures(
        matchingMembers: List<SchemaFunction>,
        args: List<FunctionArgument>,
    ) = matchingMembers.filter { it.parameters.size >= args.filterIsInstance<FunctionArgument.ValueArgument>().size }

    private fun TypeRefContext.findMemberFunction(
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

    private fun AnalysisContextView.findTopLevelFunction(
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


    private fun AnalysisContextView.findDataConstructor(
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

    private fun TypeRefContext.typeCheckFunctionCall(
        binding: ParameterArgumentBinding,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): Boolean = binding.binding.all { (param, arg) ->
        checkIsAssignable(
            getDataType(argResolution.getValue(arg)),
            resolveRef(param.type)
        )
    }

    // TODO: performance optimization (?) Don't create the binding objects until a single candidate has been chosen
    private fun bindFunctionParametersToArguments(
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

    private fun ParameterArgumentBinding.toValueBinding(argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>) =
        ParameterValueBinding(binding.mapValues { (_, arg) -> argResolution.getValue(arg) })

}
