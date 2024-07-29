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

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes


sealed interface CallMutation {
    data class RenameCall(val newName: () -> String) : CallMutation
    data class ReplaceCallArgumentMutation(val argumentAtIndex: Int, val replaceWithValue: () -> ValueNode) : CallMutation
    // TODO: mutations for adding and removing arguments?
}


sealed interface DocumentMutation {
    sealed interface HasCallMutation : DocumentMutation {
        val callMutation: CallMutation
    }

    sealed interface DocumentNodeTargetedMutation : DocumentMutation {
        val targetNode: DocumentNode

        data class RemoveNode(override val targetNode: DocumentNode) : DocumentNodeTargetedMutation
        data class ReplaceNode(override val targetNode: DocumentNode, val replaceWithNodes: () -> NewDocumentNodes) : DocumentNodeTargetedMutation
        data class InsertNodesAfterNode(override val targetNode: DocumentNode, val nodes: () -> NewDocumentNodes) : DocumentNodeTargetedMutation
        data class InsertNodesBeforeNode(override val targetNode: DocumentNode, val nodes: () -> NewDocumentNodes) : DocumentNodeTargetedMutation

        sealed interface PropertyNodeMutation : DocumentNodeTargetedMutation {
            override val targetNode: DocumentNode.PropertyNode

            data class RenamePropertyNode(override val targetNode: DocumentNode.PropertyNode, val newName: () -> String) : PropertyNodeMutation
        }

        sealed interface ElementNodeMutation : DocumentNodeTargetedMutation {
            override val targetNode: DocumentNode.ElementNode

            data class ElementNodeCallMutation(override val targetNode: DocumentNode.ElementNode, override val callMutation: CallMutation) : ElementNodeMutation, HasCallMutation

            // These might be needed when [targetNode] does not have any children; for now, these are comment-hostile.
            data class AddChildrenToEndOfBlock(override val targetNode: DocumentNode.ElementNode, val nodes: () -> NewDocumentNodes) : ElementNodeMutation
            data class AddChildrenToStartOfBlock(override val targetNode: DocumentNode.ElementNode, val nodes: () -> NewDocumentNodes) : ElementNodeMutation
        }
    }

    sealed interface ValueTargetedMutation : DocumentMutation {
        val targetValue: ValueNode

        data class ReplaceValue(override val targetValue: ValueNode, val replaceWithValue: () -> ValueNode) : ValueTargetedMutation

        sealed interface ValueFactoryNodeMutation : ValueTargetedMutation {
            override val targetValue: ValueNode.ValueFactoryNode

            data class ValueNodeCallMutation(override val targetValue: ValueNode.ValueFactoryNode, override val callMutation: CallMutation) : ValueFactoryNodeMutation, HasCallMutation
        }
    }
}
