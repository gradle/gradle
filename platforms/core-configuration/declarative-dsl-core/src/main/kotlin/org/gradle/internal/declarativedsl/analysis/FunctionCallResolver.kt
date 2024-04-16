package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionCallResolver.FunctionResolutionAndBinding
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.asChainOrNull


interface FunctionCallResolver {
    fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall
    ): ObjectOrigin.FunctionOrigin?

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
    private
    val expressionResolver: ExpressionResolver,
    private
    val codeAnalyzer: CodeAnalyzer
) : FunctionCallResolver {

    override fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall
    ): ObjectOrigin.FunctionOrigin? = with(context) {
        val argResolutions = lazy {
            var hasErrors = false
            val result = buildMap<FunctionArgument.ValueArgument, ObjectOrigin> {
                functionCall.args.filterIsInstance<FunctionArgument.ValueArgument>().forEach {
                    val resolution = expressionResolver.doResolveExpression(context, it.expr)
                    if (resolution == null) {
                        hasErrors = true
                    } else {
                        put(it, resolution)
                    }
                }
            }
            if (hasErrors) {
                errorCollector.collect(ResolutionError(functionCall, ErrorReason.UnresolvedFunctionCallArguments(functionCall)))
            }
            result
        }

        if (functionCall.args.count { it is FunctionArgument.Lambda } > 1) {
            // TODO: report functions with more than one lambda, as those are not supported for now
            return null
        }

        val overloads: List<FunctionResolutionAndBinding> = lookupFunctions(functionCall, argResolutions, context)

        return invokeIfSingleOverload(overloads, functionCall, argResolutions)?.also {
            val function = it.function
            val receiver = it.receiver
            if (function is DataMemberFunction && function.isDirectAccessOnly && receiver != null) {
                checkAccessOnCurrentReceiver(receiver, functionCall)
            }
        }
    }

    private
    fun AnalysisContext.invokeIfSingleOverload(
        overloads: List<FunctionResolutionAndBinding>,
        functionCall: FunctionCall,
        argResolutions: Lazy<Map<FunctionArgument.ValueArgument, ObjectOrigin>>
    ) = when (overloads.size) {
        0 -> {
            errorCollector.collect(ResolutionError(functionCall, ErrorReason.UnresolvedFunctionCallSignature(functionCall)))
            null
        }

        1 -> {
            val resolution = overloads.single()
            doProduceAndHandleFunctionResult(resolution, argResolutions.value, functionCall, resolution.receiver)
        }

        else -> {
            errorCollector.collect(ResolutionError(functionCall, ErrorReason.AmbiguousFunctions(overloads)))
            null
        }
    }

    // TODO: check the resolution order with the Kotlin spec
    private
    fun AnalysisContext.lookupFunctions(
        functionCall: FunctionCall,
        argResolutions: Lazy<Map<FunctionArgument.ValueArgument, ObjectOrigin>>,
        context: AnalysisContext
    ): List<FunctionResolutionAndBinding> {
        var hasErrorInReceiverResolution = false
        val overloads: List<FunctionResolutionAndBinding> = buildList {
            when (functionCall.receiver) {
                is Expr -> {
                    val receiver = expressionResolver.doResolveExpression(context, functionCall.receiver)
                    if (receiver != null) {
                        addAll(findMemberFunction(receiver, functionCall, argResolutions.value))
                    } else {
                        hasErrorInReceiverResolution = true
                    }
                }

                null -> {
                    for (scope in currentScopes.asReversed()) {
                        val implicitThisReceiver = ObjectOrigin.ImplicitThisReceiver(scope.receiver, isCurrentScopeReceiver = scope === currentScopes.last())
                        addAll(findMemberFunction(implicitThisReceiver, functionCall, argResolutions.value))
                        if (isNotEmpty()) {
                            break
                        }
                    }
                }
            }
            if (isEmpty()) {
                addAll(findDataConstructor(functionCall, argResolutions.value))
            }
            if (isEmpty()) {
                addAll(findTopLevelFunction(functionCall, argResolutions.value))
            }
            if (isEmpty() && hasErrorInReceiverResolution) {
                errorCollector.collect(ResolutionError(functionCall, ErrorReason.UnresolvedFunctionCallReceiver(functionCall)))
            }
        }
        return overloads
    }

    private
    fun AnalysisContext.doProduceAndHandleFunctionResult(
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>,
        functionCall: FunctionCall,
        receiver: ObjectOrigin?
    ): ObjectOrigin.FunctionOrigin {
        val newFunctionCallId = nextInstant()
        val valueBinding = function.binding.toValueBinding(argResolutions, functionCall.args.lastOrNull() is FunctionArgument.Lambda)
        val semantics = function.schemaFunction.semantics as FunctionSemanticsInternal

        checkBuilderSemantics(semantics, receiver, function)

        val result: ObjectOrigin.FunctionOrigin = invocationResultObjectOrigin(
            semantics, function, functionCall, valueBinding, newFunctionCallId
        )

        doRecordSemanticsSideEffects(functionCall, semantics, receiver, result, function, argResolutions)
        doAnalyzeAndCheckConfiguringSemantics(functionCall, semantics, function, result, newFunctionCallId, valueBinding)

        return result
    }

    private
    fun AnalysisContext.doAnalyzeAndCheckConfiguringSemantics(
        call: FunctionCall,
        semantics: FunctionSemanticsInternal,
        function: FunctionResolutionAndBinding,
        result: ObjectOrigin.FunctionOrigin,
        newFunctionCallId: Long,
        valueBinding: ParameterValueBinding
    ) {
        if (semantics !is FunctionSemanticsInternal.ConfigureSemantics)
            return

        val (expectsConfigureLambda, requiresConfigureLambda) = semantics.configureBlockRequirement.run { allows() to requires() }

        val lambda = call.args.filterIsInstance<FunctionArgument.Lambda>().singleOrNull()
        if (expectsConfigureLambda) {
            if (lambda != null) {
                val configureReceiver = when (semantics) {
                    is FunctionSemanticsInternal.AccessAndConfigure -> configureReceiverObject(
                        semantics,
                        function,
                        call,
                        valueBinding,
                        newFunctionCallId
                    )

                    is FunctionSemanticsInternal.AddAndConfigure -> ObjectOrigin.AddAndConfigureReceiver(result)
                }
                withScope(AnalysisScope(currentScopes.last(), configureReceiver, lambda)) {
                    codeAnalyzer.analyzeStatementsInProgramOrder(this, lambda.block.statements)
                }
            } else {
                if (requiresConfigureLambda)
                    error("expected a configuring lambda in the call of ${function.schemaFunction}, but it was not provided")
            }
        } else if (lambda != null) {
            error("a lambda is not expected in the call of ${function.schemaFunction}, but it was provided")
        }
    }

    private
    fun AnalysisContext.doRecordSemanticsSideEffects(
        call: FunctionCall,
        semantics: FunctionSemanticsInternal,
        receiver: ObjectOrigin?,
        result: ObjectOrigin.FunctionOrigin,
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ) {
        if (semantics is FunctionSemanticsInternal.AddAndConfigure) {
            require(receiver != null)
            recordAddition(receiver, result)
        }
        val assignmentMethod = when (semantics) {
            is FunctionSemanticsInternal.Builder -> AssignmentMethod.BuilderFunction(function.schemaFunction as DataBuilderFunction)
            else -> AssignmentMethod.AsConstructed
        }
        function.binding.binding.forEach { (param, arg) ->
            val paramSemantics = param.semantics
            if (paramSemantics is ParameterSemanticsInternal.StoreValueInProperty) {
                val property = paramSemantics.dataProperty
                recordAssignment(PropertyReferenceResolution(result, property), argResolutions.getValue(arg), assignmentMethod, call)
            }
        }
    }

    private
    fun checkBuilderSemantics(
        semantics: FunctionSemanticsInternal,
        receiver: ObjectOrigin?,
        function: FunctionResolutionAndBinding
    ) {
        if (semantics is FunctionSemanticsInternal.Builder) {
            require(receiver != null)

            val parameter = function.schemaFunction.parameters.singleOrNull()
                ?: error("builder functions must have a single parameter")
            parameter.semantics as? ParameterSemanticsInternal.StoreValueInProperty
                ?: error("a builder function must assign its parameter to a property")
        }
    }

    private
    fun invocationResultObjectOrigin(
        semantics: FunctionSemanticsInternal,
        function: FunctionResolutionAndBinding,
        functionCall: FunctionCall,
        valueBinding: ParameterValueBinding,
        newFunctionCallId: Long
    ) = when (semantics) {
        is FunctionSemanticsInternal.Builder -> ObjectOrigin.BuilderReturnedReceiver(
            function.schemaFunction,
            checkNotNull(function.receiver),
            functionCall,
            valueBinding,
            newFunctionCallId
        )

        is FunctionSemanticsInternal.AccessAndConfigure -> when (semantics.returnType) {
            FunctionSemanticsInternal.AccessAndConfigure.ReturnType.UNIT ->
                newObjectInvocationResult(function, valueBinding, functionCall, newFunctionCallId)

            FunctionSemanticsInternal.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT ->
                configureReceiverObject(semantics, function, functionCall, valueBinding, newFunctionCallId)
        }

        else -> newObjectInvocationResult(function, valueBinding, functionCall, newFunctionCallId)
    }

    private
    fun configureReceiverObject(
        semantics: FunctionSemanticsInternal.AccessAndConfigure,
        function: FunctionResolutionAndBinding,
        functionCall: FunctionCall,
        binding: ParameterValueBinding,
        newFunctionCallId: Long
    ): ObjectOrigin.AccessAndConfigureReceiver {
        require(function.receiver != null)
        require(functionCall.args.all { it is FunctionArgument.Lambda })
        return ObjectOrigin.AccessAndConfigureReceiver(function.receiver, function.schemaFunction, functionCall, binding, newFunctionCallId, semantics.accessor)
    }

    private
    fun preFilterSignatures(
        matchingMembers: List<SchemaFunction>,
        args: List<FunctionArgument>,
    ) = matchingMembers.filter { it.parameters.size >= args.filterIsInstance<FunctionArgument.ValueArgument>().size }

    private
    fun TypeRefContext.findMemberFunction(
        receiver: ObjectOrigin,
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>,
    ): List<FunctionResolutionAndBinding> {
        val receiverType = getDataType(receiver) as? DefaultDataClass
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

    private
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
                val fqn = DefaultFqName(packageNameParts.joinToString("."), functionCall.name)
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

    private
    fun newObjectInvocationResult(
        function: FunctionResolutionAndBinding,
        valueBinding: ParameterValueBinding,
        functionCall: FunctionCall,
        newFunctionCallId: Long
    ) = when (function.receiver) {
        is ObjectOrigin -> ObjectOrigin.NewObjectFromMemberFunction(
            function.schemaFunction as SchemaMemberFunction, function.receiver, valueBinding, functionCall, newFunctionCallId
        )

        null -> ObjectOrigin.NewObjectFromTopLevelFunction(
            function.schemaFunction, valueBinding, functionCall, newFunctionCallId
        )
    }

    private
    fun AnalysisContextView.findDataConstructor(
        functionCall: FunctionCall,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> {
        // TODO: no nested types for now
        val candidateTypes = buildList<DataTypeInternal> {
            val receiverAsChain = functionCall.receiver?.asChainOrNull()
            if (receiverAsChain != null) {
                val fqn = DefaultFqName(receiverAsChain.nameParts.joinToString("."), functionCall.name)
                val typeByFqn = schema.dataClassesByFqName[fqn]
                if (typeByFqn != null) {
                    add(typeByFqn as DefaultDataClass)
                }
            } else if (functionCall.receiver == null) {
                val importedName = imports[functionCall.name]
                if (importedName != null) {
                    val maybeType = schema.dataClassesByFqName[importedName]
                    if (maybeType != null) {
                        add(maybeType as DefaultDataClass)
                    }
                }
            }
        }
        val constructors = candidateTypes
            .flatMap { (it as? DefaultDataClass)?.constructors.orEmpty() }
            .filter { it.parameters.size == functionCall.args.size }

        return chooseMatchingOverloads(null, constructors, functionCall.args, argResolution)
    }

    private
    fun TypeRefContext.chooseMatchingOverloads(
        receiver: ObjectOrigin?,
        signatureSizeMatches: List<SchemaFunction>,
        args: List<FunctionArgument>,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): List<FunctionResolutionAndBinding> = signatureSizeMatches.mapNotNull { candidate ->
        val binding = bindFunctionParametersToArguments(
            candidate.parameters,
            args.filterIsInstance<FunctionArgument.ValueArgument>()
        ) ?: return@mapNotNull null

        (candidate.semantics as? FunctionSemanticsInternal.ConfigureSemantics)?.let { configureSemantics ->
            if (!configureSemantics.configureBlockRequirement.isValidIfLambdaIsPresent(args.lastOrNull() is FunctionArgument.Lambda)) {
                return@mapNotNull null
            }
        }

        if (!typeCheckFunctionCall(binding, argResolution)) {
            // TODO: return type mismatch in args
            return@mapNotNull null
        }

        FunctionResolutionAndBinding(receiver, candidate, binding)
    }

    private
    fun TypeRefContext.typeCheckFunctionCall(
        binding: ParameterArgumentBinding,
        argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>
    ): Boolean = binding.binding.all { (param, arg) ->
        if (arg !in argResolution) {
            // The expression for this argument has not even resolved
            return@all false
        }
        checkIsAssignable(
            getDataType(argResolution.getValue(arg)),
            resolveRef(param.type)
        )
    }

    // TODO: performance optimization (?) Don't create the binding objects until a single candidate has been chosen
    private
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
                if (arg is FunctionArgument.Named) {
                    findParameterByName(arg.name) ?: return null
                    // TODO return a named argument that does not match any parameter
                } else parameters[argIndex]

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

    private
    fun ParameterArgumentBinding.toValueBinding(argResolution: Map<FunctionArgument.ValueArgument, ObjectOrigin>, providesConfigureBlock: Boolean) =
        ParameterValueBinding(
            binding.mapValues { (_, arg) -> argResolution.getValue(arg) },
            providesConfigureBlock
        )
}
