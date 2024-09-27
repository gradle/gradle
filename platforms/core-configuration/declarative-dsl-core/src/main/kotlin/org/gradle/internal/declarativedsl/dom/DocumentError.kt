/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.dom

import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct


sealed interface DocumentError


data class SyntaxError(val parsingError: ParsingError) : DocumentError // TODO: use a better representation once integration can be done


data class UnsupportedKotlinFeature(val unsupportedConstruct: UnsupportedConstruct) : DocumentError


data class UnsupportedSyntax(val cause: UnsupportedSyntaxCause) : DocumentError


sealed interface UnsupportedSyntaxCause {
    data object DanglingExpr : UnsupportedSyntaxCause
    data object LocalVal : UnsupportedSyntaxCause
    data object AssignmentWithExplicitReceiver : UnsupportedSyntaxCause
    data object ElementWithExplicitReceiver : UnsupportedSyntaxCause
    data object ElementArgumentFormat : UnsupportedSyntaxCause
    data object ElementMultipleLambdas : UnsupportedSyntaxCause
    data object ValueFactoryCallWithComplexReceiver : UnsupportedSyntaxCause
    data object ValueFactoryCallWithNamedReference : UnsupportedSyntaxCause
    data object ValueFactoryArgumentFormat : UnsupportedSyntaxCause
    data object UnsupportedThisValue : UnsupportedSyntaxCause
    data object UnsupportedNullValue : UnsupportedSyntaxCause
    data object NamedReferenceWithExplicitReceiver : UnsupportedSyntaxCause
}
