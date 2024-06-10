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

package org.gradle.internal.declarativedsl.dom

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ErrorNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.LiteralValueNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode


/**
 * Checks the data in the content of the documents for equality structurally, ignoring:
 * * the [DeclarativeDocument] implementations,
 * * the differences in the source information, like source identifiers or the offsets in the source data,
 * * the source representations of the literal values (thus taking into account only the values),
 * * the error causes, thus considering all error nodes equal to each other but not to any other node.
 */
fun DeclarativeDocument.structurallyEqualsAsData(other: DeclarativeDocument): Boolean {
    fun ValueNode.structurallyEquals(other: ValueNode): Boolean = when (this) {
        is LiteralValueNode -> other is LiteralValueNode &&
            value == other.value

        is ValueFactoryNode -> other is ValueFactoryNode &&
            factoryName == other.factoryName &&
            values.matchesPairwise(other.values, ValueNode::structurallyEquals)
    }

    fun DocumentNode.structurallyEquals(other: DocumentNode): Boolean = when (this) {
        is ElementNode -> other is ElementNode &&
            name == other.name &&
            elementValues.matchesPairwise(other.elementValues, ValueNode::structurallyEquals) &&
            content.matchesPairwise(other.content, DocumentNode::structurallyEquals)

        is PropertyNode -> other is PropertyNode &&
            name == other.name &&
            value.structurallyEquals(other.value)

        is ErrorNode -> other is ErrorNode
    }

    return content.matchesPairwise(other.content, DocumentNode::structurallyEquals)
}


private
fun <T> Collection<T>.matchesPairwise(other: Collection<T>, pairMatches: (T, T) -> Boolean): Boolean =
    size == other.size && zip(other).all { (a, b) -> pairMatches(a, b) }
