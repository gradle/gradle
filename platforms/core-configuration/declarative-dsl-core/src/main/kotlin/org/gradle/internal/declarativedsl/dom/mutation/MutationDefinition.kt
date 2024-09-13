/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.declarative.dsl.schema.AnalysisSchema

/**
 * A user-provided definition of a potentially multistep Declarative model mutation.
 * A mutation is expressed in terms of the schema and is not aware of the contents of a specific document.
 *
 * A mutation definition may participate in the following stages of the workflow:
 * * All mutation definitions compatible with a specific schema may be displayed to a user.
 * * Mutation definitions may be checked for applicability against a specific document, and then associated with the document nodes that they are going to modify.
 * * A mutation definition may run on a user's request or in an automated workflow; the runner must provide the values for the [parameters].
 */
interface MutationDefinition {
    /**
     * A globally unique identifier for this mutation.
     * For mutations expressed with singletons, the class name (including the package) may be a good identifier.
     */
    val id: String

    /**
     * A user-visible name of the mutation. Should be a brief one-line string.
     */
    val name: String

    /**
     * A user-visible detailed description of the mutation. The description may be a multi-line string.
     */
    val description: String

    /**
     * The parameters that this mutation requests.
     * The client must provide the values for all of these parameters.
     *
     * The parameters are intentionally not available in [defineModelMutationSequence], as the sequence must be available even before the client provides
     * all the parameter values (e.g. for checking the applicability of the mutation).
     *
     * The mutation implementation may query the parameter values in [NewElementNodeProvider.ArgumentBased], [NewValueNodeProvider.ArgumentBased].
     */
    val parameters: List<MutationParameter<*>>

    /**
     * Checks if the mutation is compatible with the [projectAnalysisSchema].
     * The implementation should check if all the types and their members that the mutation works with are present in [projectAnalysisSchema].
     *
     * Returning `true` from this function means that [defineModelMutationSequence] should not fail when invoked with the same [projectAnalysisSchema].
     */
    fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean

    /**
     * Produces a sequence of [ModelMutationRequest]s that should be applied to run the mutation in the context of the [projectAnalysisSchema].
     * This function is intentionally unaware of the document content, as the model mutations are expressed in a form that does not take the
     * content of specific documents into account.
     *
     * This function is not called if [isCompatibleWithSchema] returns `false`.
     */
    fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest>
}
