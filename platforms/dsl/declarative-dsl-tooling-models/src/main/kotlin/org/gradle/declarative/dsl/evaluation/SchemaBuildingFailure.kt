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

package org.gradle.declarative.dsl.evaluation

import org.gradle.tooling.ToolingModelContract
import java.io.Serializable

/**
 * This is a tooling model interface for schema building failures, with the issues and context
 * being TAPI-friendly, not relying on kotlin-reflect.
 */
interface SchemaBuildingFailure : Serializable {
    val issue: SchemaIssue
    val context: List<FailureContext>

    @ToolingModelContract(subTypes = [
        FailureContext.ClassContext::class,
        FailureContext.MemberContext::class,
        FailureContext.TagContext::class
    ])
    interface FailureContext : Serializable {
        val userRepresentation: String

        interface ClassContext : FailureContext {
            val qualifiedName: String
        }

        interface MemberContext : FailureContext {
            val memberSignature: String
        }

        // TODO: expose more details of the different tags in the TAPI interfaces if it turns out useful
        interface TagContext : FailureContext {
            val tag: String
        }
    }
}
