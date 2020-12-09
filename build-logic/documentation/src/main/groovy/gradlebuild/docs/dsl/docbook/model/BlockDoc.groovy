/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.docbook.model

import org.w3c.dom.Element
import gradlebuild.docs.dsl.source.model.TypeMetaData

class BlockDoc implements DslElementDoc {
    private final MethodDoc blockMethod
    private final PropertyDoc blockProperty
    private final TypeMetaData type
    private boolean multiValued

    BlockDoc(MethodDoc blockMethod, PropertyDoc blockProperty, TypeMetaData type, boolean multiValued) {
        this.blockMethod = blockMethod
        this.type = type
        this.blockProperty = blockProperty
        this.multiValued = multiValued
    }

    BlockDoc forClass(ClassDoc referringClass) {
        return new BlockDoc(blockMethod.forClass(referringClass), blockProperty.forClass(referringClass), type, multiValued)
    }

    String getId() {
        return blockMethod.id
    }

    String getName() {
        return blockMethod.name
    }

    boolean isMultiValued() {
        return multiValued
    }

    TypeMetaData getType() {
        return type
    }

    Element getDescription() {
        return blockMethod.description;
    }

    List<Element> getComment() {
        return blockMethod.comment
    }

    boolean isDeprecated() {
        return blockProperty.deprecated || blockMethod.deprecated
    }

    boolean isIncubating() {
        return blockProperty.incubating || blockMethod.incubating
    }

    boolean isReplaced() {
        return blockProperty.replaced
    }

    @Override
    String getReplacement() {
        return blockProperty.replacement
    }

    PropertyDoc getBlockProperty() {
        return blockProperty
    }
}
