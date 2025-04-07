package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.AssignmentAugmentationKind
import org.gradle.declarative.dsl.schema.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.declarative.dsl.schema.DataType.TypeVariableUsage
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.declarative.dsl.schema.VarargParameter
import org.gradle.internal.declarativedsl.analysis.ExpectedTypeData.NoExpectedType
import org.gradle.internal.declarativedsl.analysis.FunctionCallResolver.FunctionResolutionAndBinding
import org.gradle.internal.declarativedsl.analysis.FunctionCallResolverImpl.ArgumentData
import org.gradle.internal.declarativedsl.language.AugmentationOperatorKind
import org.gradle.internal.declarativedsl.language.AugmentingAssignment
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.asChainOrNull


interface FunctionCallResolver {
    fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall,
        expectedType: ExpectedTypeData
    ): TypedOrigin?

    fun doResolveAugmentation(
        augmentedProperty: ObjectOrigin.PropertyReference,
        rhsResolution: TypedOrigin,
        context: AnalysisContext,
        augmentationOperatorKind: AugmentationOperatorKind,
        assignment: AugmentingAssignment,
    ): TypedOrigin?

    data class FunctionResolutionAndBinding(
        val receiver: ObjectOrigin?,
        val schemaFunction: SchemaFunction,
        val binding: ParameterArgumentBinding
    )
}


data class ParameterArgumentBinding(
    val binding: Map<DataParameter, FunctionArgument.ValueLikeArgument>
)


class FunctionCallResolverImpl(
    private
    val expressionResolver: ExpressionResolver,
    private
    val codeAnalyzer: CodeAnalyzer
) : FunctionCallResolver {

    internal
    sealed interface ArgumentData {
        data class BoundArguments(
            val argumentResolution: Lazy<Map<FunctionArgument.SingleValueArgument, TypedOrigin>>
        ) : ArgumentData

        data object NoResolvedArguments : ArgumentData
    }

    private val defaultArgumentResolutionStrategy = DefaultArgumentResolutionStrategy(expressionResolver)

    override fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall,
        expectedType: ExpectedTypeData
    ): TypedOrigin? {
        return doFunctionCallResolution(context, DefaultFunctionLookupStrategy, defaultArgumentResolutionStrategy, functionCall, expectedType)
    }

    override fun doResolveAugmentation(
        augmentedProperty: ObjectOrigin.PropertyReference,
        rhsResolution: TypedOrigin,
        context: AnalysisContext,
        augmentationOperatorKind: AugmentationOperatorKind,
        assignment: AugmentingAssignment,
    ): TypedOrigin? {
        val lhsArg = FunctionArgument.Positional(augmentedProperty.originElement as Expr, augmentedProperty.originElement.sourceData)
        val rhsArg = FunctionArgument.Positional(rhsResolution.objectOrigin.originElement as Expr, rhsResolution.objectOrigin.originElement.sourceData)

        return doFunctionCallResolution(
            context,
            AugmentationLookupStrategy(context.resolveRef(augmentedProperty.property.valueType), augmentationOperatorKind),
            AugmentationArgumentResolutionStrategy(lhsArg, augmentedProperty, rhsArg, rhsResolution),
            FunctionCall(
                null,
                augmentationOperatorKind.operatorToken, // name is irrelevant here
                listOf(lhsArg, rhsArg),
                assignment.sourceData
            ),
            ExpectedTypeData.ExpectedByProperty(augmentedProperty.property.valueType)
        )
    }

    class AugmentationArgumentResolutionStrategy(
        private val lhsArg: FunctionArgument.Positional,
        private val augmentedProperty: ObjectOrigin.PropertyReference,
        private val rhsArg: FunctionArgument.Positional,
        private val rhsResolution: TypedOrigin
    ) : ArgumentResolutionStrategy {
        override fun resolveArgument(
            context: AnalysisContext,
            argument: FunctionArgument.SingleValueArgument,
            expectedType: ExpectedTypeData
        ): TypedOrigin? = when (argument) {
            lhsArg -> TypedOrigin(augmentedProperty, context.resolveRef(augmentedProperty.property.valueType))
            rhsArg -> rhsResolution
            else -> null
        }
    }

    private fun doFunctionCallResolution(
        context: AnalysisContext,
        lookupStrategy: LookupStrategy,
        argumentResolutionStrategy: ArgumentResolutionStrategy,
        functionCall: FunctionCall,
        expectedType: ExpectedTypeData
    ): TypedOrigin? = with(context) {
        // Avoid resolving the receiver more than once by resolving it here lazily.
        // This prevents unwanted duplicate resolution side effects, e.g., in `id("com.example").version("1.0")`, resolving `id(...)` twice would otherwise record multiple objects produced by `id`.
        val lazyReceiverResolution = lazy {
            functionCall.receiver?.let { receiver ->
                expressionResolver.doResolveExpression(context, receiver, NoExpectedType).also { receiverResolution ->
                    if (receiverResolution == null && receiver.asChainOrNull() == null)
                        errorCollector.collect(ResolutionError(functionCall, ErrorReason.UnresolvedFunctionCallReceiver(functionCall)))
                }
            }
        }

        // First, try to resolve a single function. If there is just one, we can use its signature for expected types and generic type substitution.
        val singleMatchingFunction = lookupStrategy.optimisticSingleMatchLookup(context, lazyReceiverResolution, functionCall)

        val typeSubstitution = singleMatchingFunction?.let {
            // Type substitution in DCL is simplistic: it is only computed based on the expected type and does not infer types from arguments
            computeGenericTypeSubstitution((expectedType as? ExpectedTypeData.HasExpectedType)?.type, it.schemaFunction.returnValueType)
        } ?: emptyMap()

        val expectedArgTypes = expectedArgTypesForFunction(singleMatchingFunction).mapValues { (_, type) -> applyTypeSubstitution(resolveRef(type), typeSubstitution) }

        val lazyArgResolutions = lazy {
            var hasErrors = false
            val result = buildMap<FunctionArgument.SingleValueArgument, TypedOrigin> {
                functionCall.args.filterIsInstance<FunctionArgument.SingleValueArgument>().forEach { arg ->
                    val resolution = argumentResolutionStrategy.resolveArgument(
                        context, arg, expectedArgTypes[arg]?.let { ExpectedTypeData.ExpectedByParameter(it.ref) } ?: NoExpectedType
                    )
                    if (resolution == null) {
                        hasErrors = true
                    } else {
                        put(arg, resolution)
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

        val overloads: List<FunctionResolutionAndBinding> = lookupStrategy.lookup(context, lazyReceiverResolution, functionCall, ArgumentData.BoundArguments(lazyArgResolutions), typeSubstitution)

        val resultOriginOrNull = invokeIfSingleOverload(overloads, functionCall, lazyArgResolutions, typeSubstitution)

        resultOriginOrNull?.let {
            TypedOrigin(it, applyTypeSubstitution(resolveRef(resultOriginOrNull.function.returnValueType), typeSubstitution))
        }
    }

    private fun AnalysisContextView.expectedArgTypesForFunction(singleMatchingFunction: FunctionResolutionAndBinding?): Map<FunctionArgument.ValueLikeArgument, DataTypeRef> = singleMatchingFunction
        ?.binding?.binding?.entries
        // In the binding map, the varargs are grouped. To use the expected type for the single element within the vararg, ungroup them here:
        ?.flatMap { (param, argOrVararg) ->
            if (argOrVararg is FunctionArgument.GroupedVarargs)
                argOrVararg.elementArgs.map { arg -> param to arg }
            else listOf(param to argOrVararg)
        }
        ?.associate { (param, arg) ->
            arg to if (param is VarargParameter) getElementTypeFromVarargType(param).ref else param.type
        } ?: emptyMap()

    private
    fun AnalysisContext.invokeIfSingleOverload(
        overloads: List<FunctionResolutionAndBinding>,
        functionCall: FunctionCall,
        argResolutions: Lazy<Map<FunctionArgument.SingleValueArgument, TypedOrigin>>,
        typeSubstitution: Map<TypeVariableUsage, DataType>
    ) = when (overloads.size) {
        0 -> {
            errorCollector.collect(ResolutionError(functionCall, ErrorReason.UnresolvedFunctionCallSignature(functionCall)))
            null
        }

        1 -> {
            val resolution = overloads.single()
            doProduceAndHandleFunctionResultOrNull(resolution, argResolutions.value, functionCall, resolution.receiver, typeSubstitution)
        }

        else -> {
            errorCollector.collect(ResolutionError(functionCall, ErrorReason.AmbiguousFunctions(overloads)))
            null
        }
    }

    private interface LookupStrategy {
        fun lookup(
            context: AnalysisContextView,
            receiver: Lazy<TypedOrigin?>,
            call: FunctionCall,
            args: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding>
    }

    /**
     * Before we even have the argument types, we try to look the functions up by the name.
     * If there is a single matching overload, then its parameter types contribute to the expected argument types.
     * If there is more than one overload, the process stops, as the overload resolution needs argument types anyway.
     */
    private fun LookupStrategy.optimisticSingleMatchLookup(
        context: AnalysisContextView,
        receiver: Lazy<TypedOrigin?>,
        call: FunctionCall
    ): FunctionResolutionAndBinding? = lookup(context, receiver, call, ArgumentData.NoResolvedArguments, emptyMap()).singleOrNull()

    private object DefaultFunctionLookupStrategy : LookupStrategy {
        override fun lookup(
            context: AnalysisContextView,
            receiver: Lazy<TypedOrigin?>,
            call: FunctionCall,
            args: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> =
            context.lookupNamedFunctionCall(receiver, call, args, typeSubstitution)

        // TODO: check the resolution order with the Kotlin spec
        private fun AnalysisContextView.lookupNamedFunctionCall(
            receiverResolution: Lazy<TypedOrigin?>,
            functionCall: FunctionCall,
            argResolutions: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> {
            val overloads: List<FunctionResolutionAndBinding> = buildList {
                when (functionCall.receiver) {
                    is Expr -> {
                        val receiver = receiverResolution.value
                        if (receiver != null) {
                            addAll(findMemberFunction(receiver.objectOrigin, functionCall, argResolutions, typeSubstitution))
                        }
                    }

                    null -> {
                        for (scope in currentScopes.asReversed()) {
                            val implicitThisReceiver = ObjectOrigin.ImplicitThisReceiver(scope.receiver, isCurrentScopeReceiver = scope === currentScopes.last())
                            addAll(findMemberFunction(implicitThisReceiver, functionCall, argResolutions, typeSubstitution))
                            if (isNotEmpty()) {
                                break
                            }
                        }
                    }
                }
                if (isEmpty()) {
                    addAll(findDataConstructor(functionCall, argResolutions, typeSubstitution))
                }
                if (isEmpty()) {
                    addAll(findTopLevelFunction(functionCall, argResolutions, typeSubstitution))
                }
            }
            return overloads
        }

        private
        fun TypeRefContext.findMemberFunction(
            receiver: ObjectOrigin,
            functionCall: FunctionCall,
            argResolution: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> {
            val receiverType = getDataType(receiver) as? DataClass
                ?: return emptyList()
            val functionName = functionCall.name
            val matchingMembers = receiverType.memberFunctions.filter { it.simpleName == functionName }
            // TODO: support optional parameters?
            // TODO: support at least minimal overload resolution?
            val args = functionCall.args

            // TODO: lambdas are handled in a special way and don't participate in signature matching now
            val signatureSizeMatches = preFilterSignatures(matchingMembers, args)

            return chooseMatchingOverloads(receiver, signatureSizeMatches, args, argResolution, typeSubstitution)
        }

        private
        fun AnalysisContextView.findDataConstructor(
            functionCall: FunctionCall,
            argResolution: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> {
            // TODO: no nested types for now
            val candidateTypes = buildList<DataType> {
                val receiverAsChain = functionCall.receiver?.asChainOrNull()
                if (receiverAsChain != null) {
                    val fqn = DefaultFqName(receiverAsChain.nameParts.joinToString("."), functionCall.name)
                    val typeByFqn = schema.dataClassTypesByFqName[fqn]
                    if (typeByFqn != null) {
                        add(typeByFqn)
                    }
                } else if (functionCall.receiver == null) {
                    val importedName = imports[functionCall.name]
                    if (importedName != null) {
                        val maybeType = schema.dataClassTypesByFqName[importedName]
                        if (maybeType != null) {
                            add(maybeType)
                        }
                    }
                }
            }
            val constructors = candidateTypes
                .flatMap { (it as? DataClass)?.constructors.orEmpty() }
                .filter { it.parameters.size == functionCall.args.size }

            return chooseMatchingOverloads(null, constructors, functionCall.args, argResolution, typeSubstitution)
        }

        private
        fun AnalysisContextView.findTopLevelFunction(
            functionCall: FunctionCall,
            argResolution: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> {
            val args = functionCall.args
            val receiver = functionCall.receiver

            // TODO: extension functions are not supported now; so it's either an FQN function reference or imported one
            if (receiver is NamedReference && receiver.asChainOrNull() != null || receiver == null) {
                val packageNameParts = receiver?.asChainOrNull()?.nameParts.orEmpty()
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
                    chooseMatchingOverloads(null, preFilterSignatures(candidates, args), args, argResolution, typeSubstitution)

                // TODO: report overload ambiguity?
                return matchingOverloads
            } else {
                return emptyList()
            }
        }
    }

    private class AugmentationLookupStrategy(val lhsType: DataType, val operator: AugmentationOperatorKind) : LookupStrategy {
        override fun lookup(
            context: AnalysisContextView,
            receiver: Lazy<TypedOrigin?>,
            call: FunctionCall,
            args: ArgumentData,
            typeSubstitution: Map<TypeVariableUsage, DataType>
        ): List<FunctionResolutionAndBinding> {
            if (lhsType !is DataType.ClassDataType) {
                return emptyList()
            }

            val candidates = context.schema.assignmentAugmentationsByTypeName[lhsType.name]?.filter { augmentation ->
                augmentation.kind.matchesOperator(operator)
            }?.map { it.function }.orEmpty()

            return context.chooseMatchingOverloads(null, candidates, call.args, args, typeSubstitution)
        }

        private fun AssignmentAugmentationKind.matchesOperator(operatorKind: AugmentationOperatorKind) =
            when (operatorKind) {
                AugmentationOperatorKind.PlusAssign -> this is AssignmentAugmentationKind.Plus
            }
    }

    private interface ArgumentResolutionStrategy {
        fun resolveArgument(
            context: AnalysisContext,
            argument: FunctionArgument.SingleValueArgument,
            expectedType: ExpectedTypeData
        ): TypedOrigin?
    }

    private class DefaultArgumentResolutionStrategy(val expressionResolver: ExpressionResolver) : ArgumentResolutionStrategy {
        override fun resolveArgument(
            context: AnalysisContext,
            argument: FunctionArgument.SingleValueArgument,
            expectedType: ExpectedTypeData
        ): TypedOrigin? = expressionResolver.doResolveExpression(context, argument.expr, expectedType)
    }

    private
    fun AnalysisContext.doProduceAndHandleFunctionResultOrNull(
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.SingleValueArgument, TypedOrigin>,
        functionCall: FunctionCall,
        receiver: ObjectOrigin?,
        typeSubstitution: Map<TypeVariableUsage, DataType>
    ): ObjectOrigin.FunctionOrigin? {
        val newFunctionCallId = nextCallId()
        val valueBinding = toValueBinding(functionCall, function.binding, argResolutions, functionCall.args.lastOrNull() is FunctionArgument.Lambda, typeSubstitution)
        val semantics = function.schemaFunction.semantics

        checkBuilderSemantics(semantics, receiver, function)

        val result: ObjectOrigin.FunctionInvocationOrigin = invocationResultObjectOrigin(
            semantics, function, functionCall, valueBinding, newFunctionCallId
        )

        if (!doCheckParameterSemantics(result, functionCall)) {
            return null
        }

        doRecordSemanticsSideEffects(functionCall, semantics, receiver, result, function, argResolutions)
        doAnalyzeAndCheckConfiguringSemantics(functionCall, semantics, function, result, newFunctionCallId, valueBinding)

        return result
    }

    private fun AnalysisContext.doCheckParameterSemantics(functionOrigin: ObjectOrigin.FunctionInvocationOrigin, functionCall: FunctionCall): Boolean =
        if (functionOrigin.function.semantics is FunctionSemantics.AccessAndConfigure) {
            functionOrigin.parameterBindings.bindingMap.all { (param, arg) ->
                checkIdentityKeyParameterSemantics(functionCall, param, arg.objectOrigin)
            }
        } else true

    private
    fun AnalysisContext.doAnalyzeAndCheckConfiguringSemantics(
        call: FunctionCall,
        semantics: FunctionSemantics,
        function: FunctionResolutionAndBinding,
        result: ObjectOrigin.FunctionOrigin,
        newOperationId: OperationId,
        valueBinding: ParameterValueBinding
    ) {
        if (semantics !is FunctionSemantics.ConfigureSemantics)
            return

        val configureReceiver = when (semantics) {
            is FunctionSemantics.AccessAndConfigure -> configureReceiverObject(
                semantics,
                function,
                call,
                valueBinding,
                newOperationId
            ).also {
                result.receiver?.let { receiver -> recordNestedObjectAccess(receiver, it) }
            }

            is FunctionSemantics.AddAndConfigure -> ObjectOrigin.AddAndConfigureReceiver(result)
        }

        val lambda = call.args.filterIsInstance<FunctionArgument.Lambda>().singleOrNull()
        val (expectsConfigureLambda, requiresConfigureLambda) = semantics.configureBlockRequirement.run { allows to requires }
        if (expectsConfigureLambda) {
            if (lambda != null) {
                withScope(AnalysisScope(currentScopes.last(), configureReceiver, lambda)) {
                    codeAnalyzer.analyzeStatementsInProgramOrder(this, lambda.block.statements)
                }
            } else {
                if (requiresConfigureLambda)
                    error("Expected a configuring lambda in the call of ${function.schemaFunction.format(function.receiver)}, but it was not provided")
            }
        } else if (lambda != null) {
            error("A lambda is not expected in the call of ${function.schemaFunction.format(function.receiver)}, but it was provided")
        }
    }

    private
    fun AnalysisContext.doRecordSemanticsSideEffects(
        call: FunctionCall,
        semantics: FunctionSemantics,
        receiver: ObjectOrigin?,
        result: ObjectOrigin.FunctionOrigin,
        function: FunctionResolutionAndBinding,
        argResolutions: Map<FunctionArgument.SingleValueArgument, TypedOrigin>
    ) {
        if (semantics is FunctionSemantics.AddAndConfigure) {
            require(receiver != null)
            recordAddition(receiver, result)
        }
        val assignmentMethod = when (semantics) {
            is FunctionSemantics.Builder -> AssignmentMethod.BuilderFunction(function.schemaFunction as DataBuilderFunction)
            else -> AssignmentMethod.AsConstructed
        }
        function.binding.binding.forEach { (param, arg) ->
            val argumentOrigin by lazy {
                when (arg) {
                    is FunctionArgument.SingleValueArgument -> argResolutions.getValue(arg)
                    is FunctionArgument.GroupedVarargs -> error("unexpected semantics for vararg argument $arg: ${param.semantics}")
                }
            }

            when (val paramSemantics = param.semantics) {
                is ParameterSemantics.StoreValueInProperty -> {
                    val property = paramSemantics.dataProperty
                    recordAssignment(PropertyReferenceResolution(result, property), argumentOrigin, assignmentMethod, call)
                }

                is ParameterSemantics.IdentityKey -> {
                    paramSemantics.basedOnProperty?.let { property ->
                        recordAssignment(PropertyReferenceResolution(result, property), argumentOrigin, assignmentMethod, call)
                    }
                }

                is ParameterSemantics.Unknown -> Unit
            }
        }
    }

    private fun AnalysisContext.checkIdentityKeyParameterSemantics(functionCall: FunctionCall, parameter: DataParameter, argumentOrigin: ObjectOrigin): Boolean =
        if (argumentOrigin !is ObjectOrigin.ConstantOrigin) {
            errorCollector.collect(ResolutionError(functionCall, ErrorReason.OpaqueArgumentForIdentityParameter(functionCall, parameter, argumentOrigin)))
            false
        } else true


    private
    fun checkBuilderSemantics(
        semantics: FunctionSemantics,
        receiver: ObjectOrigin?,
        function: FunctionResolutionAndBinding
    ) {
        if (semantics is FunctionSemantics.Builder) {
            require(receiver != null)

            val parameter = function.schemaFunction.parameters.singleOrNull()
                ?: interpretationFailure("${function.schemaFunction.format(function.receiver, lowercase = false)} is a builder function and should have a single parameter")
            parameter.semantics as? ParameterSemantics.StoreValueInProperty
                ?: interpretationFailure("${function.schemaFunction.format(function.receiver, lowercase = false)} is a builder function and must assign its parameter to a property")
        }
    }

    private
    fun invocationResultObjectOrigin(
        semantics: FunctionSemantics,
        function: FunctionResolutionAndBinding,
        functionCall: FunctionCall,
        valueBinding: ParameterValueBinding,
        newOperationId: OperationId
    ) = when (semantics) {
        is FunctionSemantics.Builder -> ObjectOrigin.BuilderReturnedReceiver(
            function.schemaFunction,
            checkNotNull(function.receiver),
            functionCall,
            valueBinding,
            newOperationId
        )

        is FunctionSemantics.AccessAndConfigure -> when (semantics.returnType) {
            is FunctionSemantics.AccessAndConfigure.ReturnType.Unit ->
                newObjectInvocationResult(function, valueBinding, functionCall, newOperationId)

            is FunctionSemantics.AccessAndConfigure.ReturnType.ConfiguredObject ->
                configureReceiverObject(semantics, function, functionCall, valueBinding, newOperationId)
        }

        else -> newObjectInvocationResult(function, valueBinding, functionCall, newOperationId)
    }

    private
    fun configureReceiverObject(
        semantics: FunctionSemantics.AccessAndConfigure,
        function: FunctionResolutionAndBinding,
        functionCall: FunctionCall,
        binding: ParameterValueBinding,
        newOperationId: OperationId
    ): ObjectOrigin.AccessAndConfigureReceiver {
        require(function.receiver != null)
        return ObjectOrigin.AccessAndConfigureReceiver(function.receiver, function.schemaFunction, functionCall, binding, newOperationId, semantics.accessor)
    }

    private
    fun newObjectInvocationResult(
        function: FunctionResolutionAndBinding,
        valueBinding: ParameterValueBinding,
        functionCall: FunctionCall,
        newOperationId: OperationId
    ) = when (function.receiver) {
        is ObjectOrigin -> ObjectOrigin.NewObjectFromMemberFunction(
            function.schemaFunction as SchemaMemberFunction, function.receiver, valueBinding, functionCall, newOperationId
        )

        null -> ObjectOrigin.NewObjectFromTopLevelFunction(
            function.schemaFunction, valueBinding, functionCall, newOperationId
        )
    }

    private
    fun AnalysisContextView.toValueBinding(
        functionCall: FunctionCall,
        parameterArgumentBinding: ParameterArgumentBinding,
        argResolution: Map<FunctionArgument.SingleValueArgument, TypedOrigin>,
        providesConfigureBlock: Boolean,
        typeSubstitution: Map<TypeVariableUsage, DataType>
    ) =
        ParameterValueBinding(
            parameterArgumentBinding.binding.mapValues { (param, arg) ->
                if (param is VarargParameter) {
                    val separateArgs = parameterArgumentBinding.binding[param] as FunctionArgument.GroupedVarargs
                    val argValues = separateArgs.elementArgs.map(argResolution::getValue)
                    val type = applyTypeSubstitution(resolveRef(param.type), typeSubstitution) as DataType.ParameterizedTypeInstance
                    val elementType = getElementTypeFromVarargType(type)
                    TypedOrigin(
                        ObjectOrigin.GroupedVarargValue(
                            functionCall,
                            argValues.map { it.objectOrigin },
                            elementType,
                            type
                        ), type
                    )
                } else {
                    argResolution.getValue(arg as FunctionArgument.SingleValueArgument)
                }
            },
            providesConfigureBlock
        )
}

private
fun preFilterSignatures(
    matchingMembers: List<SchemaFunction>,
    args: List<FunctionArgument>,
) = matchingMembers.filter { it.parameters.any { it is VarargParameter } || it.parameters.size >= args.filterIsInstance<FunctionArgument.SingleValueArgument>().size }

private
fun TypeRefContext.chooseMatchingOverloads(
    receiver: ObjectOrigin?,
    signatureSizeMatches: List<SchemaFunction>,
    args: List<FunctionArgument>,
    argResolution: ArgumentData,
    typeSubstitution: Map<TypeVariableUsage, DataType>
): List<FunctionResolutionAndBinding> = signatureSizeMatches.mapNotNull { candidate ->
    val binding = bindFunctionParametersToArguments(
        candidate.parameters,
        args.filterIsInstance<FunctionArgument.SingleValueArgument>()
    ) ?: return@mapNotNull null

    (candidate.semantics as? FunctionSemantics.ConfigureSemantics)?.let { configureSemantics ->
        if (!configureSemantics.configureBlockRequirement.isValidIfLambdaIsPresent(args.lastOrNull() is FunctionArgument.Lambda)) {
            return@mapNotNull null
        }
    }

    if (argResolution is ArgumentData.BoundArguments && !typeCheckFunctionCall(binding, argResolution.argumentResolution.value, typeSubstitution)) {
        // TODO: return type mismatch in args
        return@mapNotNull null
    }

    FunctionResolutionAndBinding(receiver, candidate, binding)
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

    val varargParameter = parameters.singleOrNull { it is VarargParameter }
    val indexOfVarargParameter = parameters.indexOf(varargParameter)

    val bindingMap = mutableMapOf<DataParameter, FunctionArgument.ValueLikeArgument>()
    arguments.forEachIndexed { argIndex, arg ->
        if (argIndex < lastPositionalArgIndex && arg is FunctionArgument.Named && arg.name != parameters[argIndex].name) {
            // TODO: report mixed positional and named arguments?
            return@bindFunctionParametersToArguments null
        }

        if (arg is FunctionArgument.Named && parameters.none { it.name == arg.name }) {
            // TODO: report non-matching candidate?
            return@bindFunctionParametersToArguments null
        }

        val param = if (arg is FunctionArgument.Named) {
            findParameterByName(arg.name) ?: return null
            // TODO return a named argument that does not match any parameter
        } else {
            parameters.getOrNull(argIndex) ?: varargParameter.takeIf { argIndex >= indexOfVarargParameter }
        }

        if (param != varargParameter && param in bindingMap) {
            // TODO: report arg conflict
            return@bindFunctionParametersToArguments null
        }

        if (param != null && arg is FunctionArgument.SingleValueArgument) {
            bindingMap[param] = if (param == varargParameter) {
                (bindingMap[param] as? FunctionArgument.GroupedVarargs)?.let {
                    it.copy(it.elementArgs + arg)
                } ?: FunctionArgument.GroupedVarargs(listOf(arg))
            } else arg
        }
    }

    varargParameter?.let {
        if (varargParameter !in bindingMap) {
            bindingMap[varargParameter] = FunctionArgument.GroupedVarargs(emptyList())
        }
    }

    return if (parameters.all { it.isDefault || it in bindingMap }) {
        ParameterArgumentBinding(bindingMap)
    } else {
        null
    }
}

private
fun TypeRefContext.typeCheckFunctionCall(
    binding: ParameterArgumentBinding,
    argResolution: Map<FunctionArgument.SingleValueArgument, TypedOrigin>,
    typeSubstitution: Map<TypeVariableUsage, DataType>
): Boolean = binding.binding.all { (param, arg) ->
    fun isSingleArgumentAssignable(
        param: DataParameter,
        arg: FunctionArgument.SingleValueArgument,
    ) = arg in argResolution && checkIsAssignable(
        argResolution.getValue(arg).inferredType,
        resolveRef(param.type),
        typeSubstitution
    )

    when (arg) {
        is FunctionArgument.SingleValueArgument -> isSingleArgumentAssignable(param, arg)
        is FunctionArgument.GroupedVarargs -> arg.elementArgs.all { isSingleArgumentAssignable(param, it) }
    }

    when (arg) {
        is FunctionArgument.SingleValueArgument ->
            isSingleArgumentAssignable(param, arg)

        is FunctionArgument.GroupedVarargs -> {
            val elementType = getElementTypeFromVarargType(param)
            arg.elementArgs.all { singleArgument ->
                singleArgument in argResolution && checkIsAssignable(argResolution.getValue(singleArgument).inferredType, elementType, typeSubstitution)
            }
        }
    }
}

private fun TypeRefContext.getElementTypeFromVarargType(param: DataParameter): DataType =
    getElementTypeFromVarargType(resolveRef(param.type) as DataType.ParameterizedTypeInstance)

private fun TypeRefContext.getElementTypeFromVarargType(type: DataType.ParameterizedTypeInstance): DataType =
    resolveRef((type.typeArguments.single() as ConcreteTypeArgument).type)
