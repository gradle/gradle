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

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse


fun valueFromString(string: String): DeclarativeDocument.ValueNode? =
    DefaultLanguageTreeBuilder().build(parse("value = $string"), SourceIdentifier("<synthetic>"))
        .toDocument()
        .content.filterIsInstance<DeclarativeDocument.DocumentNode.PropertyNode>()
        .singleOrNull()
        ?.value


fun documentNodeFromString(string: String): DeclarativeDocument.DocumentNode? =
    DefaultLanguageTreeBuilder().build(parse(string), SourceIdentifier("<synthetic>"))
        .toDocument()
        .content
        .singleOrNull()


fun elementFromString(string: String): DeclarativeDocument.DocumentNode.ElementNode? =
    documentNodeFromString(string) as? DeclarativeDocument.DocumentNode.ElementNode
