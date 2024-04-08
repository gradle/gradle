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


interface ResolvedDeclarativeDocument : DeclarativeDocument {
    override val content: Collection<ResolvedDocumentNode>

    sealed interface ResolvedDocumentNode : DeclarativeDocument.DocumentNode {
        val resolution: DocumentResolution

        sealed interface ResolvedPropertyNode : DeclarativeDocument.DocumentNode.PropertyNode, ResolvedDocumentNode {
            override val value: ResolvedValueNode
            override val resolution: DocumentResolution.PropertyResolution
        }

        sealed interface ResolvedElementNode : DeclarativeDocument.DocumentNode.ElementNode, ResolvedDocumentNode {
            override val content: Collection<ResolvedDocumentNode>
            override val resolution: DocumentResolution.ElementResolution
            override val elementValues: Collection<ResolvedValueNode>
        }

        sealed interface ResolvedErrorNode : DeclarativeDocument.DocumentNode.ErrorNode, ResolvedDocumentNode
    }

    sealed interface ResolvedValueNode : DeclarativeDocument.ValueNode {
        val resolution: DocumentResolution.ValueResolution

        sealed interface ResolvedLiteralValueNode : DeclarativeDocument.ValueNode.LiteralValueNode, ResolvedValueNode {
            override val resolution: DocumentResolution.ValueResolution
                get() = DocumentResolution.ValueResolution.LiteralValueResolved(value)
        }

        sealed interface ResolvedValueFactoryNode : DeclarativeDocument.ValueNode.ValueFactoryNode, ResolvedValueNode {
            override val resolution: DocumentResolution.ValueResolution.ValueFactoryResolution
            override val values: List<ResolvedValueNode>
        }
    }
}
