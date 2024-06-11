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

import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier


interface DeclarativeDocument {
    val content: Collection<DocumentNode>
    val sourceIdentifier: SourceIdentifier

    sealed interface Node {
        val sourceData: SourceData
    }

    sealed interface DocumentNode : Node {
        override val sourceData: SourceData

        interface PropertyNode : DocumentNode {
            val name: String
            val value: ValueNode
        }

        interface ElementNode : DocumentNode {
            val name: String
            val elementValues: Collection<ValueNode>
            val content: Collection<DocumentNode>
        }

        interface ErrorNode : DocumentNode {
            val errors: Collection<DocumentError>
        }
    }

    sealed interface ValueNode : Node {
        override val sourceData: SourceData

        interface LiteralValueNode : ValueNode {
            val value: Any
        }

        interface ValueFactoryNode : ValueNode {
            val factoryName: String
            val values: List<ValueNode> // TODO: restrict to a single value? or even a single literal?
        }
    }
}
