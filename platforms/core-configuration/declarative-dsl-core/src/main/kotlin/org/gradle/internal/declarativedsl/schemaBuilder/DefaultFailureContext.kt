/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.evaluation.SchemaBuildingFailure.FailureContext

object DefaultFailureContext {
    data class ClassFailureContext(override val qualifiedName: String) : FailureContext.ClassContext {
        override val userRepresentation: String
            get() = "class '${qualifiedName}'"
    }

    data class MemberFailureContext(
        override val memberSignature: String,
        override val userRepresentation: String
    ) : FailureContext.MemberContext

    data class TagFailureContext(override val tag: String) : FailureContext.TagContext {
        override val userRepresentation: String
            get() = tag
    }
}
