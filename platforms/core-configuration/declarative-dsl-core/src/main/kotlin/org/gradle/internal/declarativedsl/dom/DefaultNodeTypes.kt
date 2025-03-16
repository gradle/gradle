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

import org.gradle.internal.declarativedsl.language.SourceData


internal
data class DefaultPropertyNode(
    override val name: String,
    override val sourceData: SourceData,
    override val value: DeclarativeDocument.ValueNode
) : DeclarativeDocument.DocumentNode.PropertyNode {
    override fun toString() = "property($name, $value)"
}


internal
data class DefaultElementNode(
    override val name: String,
    override val sourceData: SourceData,
    override val elementValues: List<DeclarativeDocument.ValueNode>,
    override val content: List<DeclarativeDocument.DocumentNode>,
) : DeclarativeDocument.DocumentNode.ElementNode {
    override fun toString() = "element($name, [${elementValues.joinToString()}], content.size = ${content.size})"
}


internal
data class DefaultErrorNode(
    override val sourceData: SourceData,
    override val errors: Collection<DocumentError>
) : DeclarativeDocument.DocumentNode.ErrorNode {
    override fun toString() = "error(${errors.joinToString()})"
}


internal
data class DefaultLiteralNode(
    override val value: Any,
    override val sourceData: SourceData
) : DeclarativeDocument.ValueNode.LiteralValueNode {
    override fun toString() = "literal($value)"
}


internal
data class DefaultNamedReferenceNode(
    override val referenceName: String,
    override val sourceData: SourceData
) : DeclarativeDocument.ValueNode.NamedReferenceNode {
    override fun toString() = "namedReference($referenceName)"
}


internal
data class DefaultValueFactoryNode(
    override val factoryName: String,
    override val sourceData: SourceData,
    override val values: List<DeclarativeDocument.ValueNode>
) : DeclarativeDocument.ValueNode.ValueFactoryNode {
    override fun toString() = "valueFactory($factoryName, [${values.joinToString()}])"
}
