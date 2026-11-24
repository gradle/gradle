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

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember.TypedFunction


data class ScopeLocation(val elements: List<ScopeLocationElement>) {
    companion object {
        fun fromTopLevel(): ScopeLocation = ScopeLocation(listOf())
        fun inAnyScope(): ScopeLocation = ScopeLocation(listOf(ScopeLocationElement.InAllNestedScopes))
    }
}


fun ScopeLocation.alsoInNestedScopes() = ScopeLocation(this.elements + ScopeLocationElement.InAllNestedScopes)


fun ScopeLocation.inObjectsOfType(dataClass: DataClass) = ScopeLocation(this.elements + ScopeLocationElement.InNestedScopes(NestedScopeSelector.NestedObjectsOfType(dataClass)))


fun ScopeLocation.inObjectsConfiguredBy(function: TypedFunction) = ScopeLocation(this.elements + ScopeLocationElement.InNestedScopes(NestedScopeSelector.ObjectsConfiguredBy(function)))


sealed interface ScopeLocationElement {
    data object InAllNestedScopes : ScopeLocationElement
    data class InNestedScopes(val nestedScopeSelector: NestedScopeSelector) : ScopeLocationElement
}


sealed interface NestedScopeSelector {
    data class NestedObjectsOfType(val type: DataClass) : NestedScopeSelector
    data class ObjectsConfiguredBy(val function: TypedFunction, val argumentsPattern: ArgumentsPattern = ArgumentsPattern.AnyArguments) : NestedScopeSelector
}


sealed interface IfNotFoundBehavior {
    data object Ignore : IfNotFoundBehavior
    data object FailAndReport : IfNotFoundBehavior
}


sealed interface ArgumentsPattern {
    data object AnyArguments : ArgumentsPattern

    data class MatchesArguments(
        /**
         * These are arguments that a call site should have. They are matched as [DeclarativeDocument.ValueNode]s structurally, and their
         * resolutions are considered equal if they are resolved to the same declaration or are both unresolved (for any reason). Actual arguments that are present at
         * a call site but not in this map are considered as matching arguments.
         */
        val shouldHaveArguments: Map<DataParameter, ValueMatcher>
    ) : ArgumentsPattern
}


sealed interface ValueMatcher {
    data class MatchLiteral(val literalValue: Any) : ValueMatcher
    data class MatchValueFactory(val valueFactory: SchemaMemberFunction, val args: ArgumentsPattern) : ValueMatcher
}
