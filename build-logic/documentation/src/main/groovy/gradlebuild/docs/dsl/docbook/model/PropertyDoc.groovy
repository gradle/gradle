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

import gradlebuild.docs.dsl.source.model.PropertyMetaData
import org.w3c.dom.Element
import gradlebuild.docs.dsl.source.model.ClassMetaData

class PropertyDoc implements DslElementDoc {
    private final String id
    private final String name
    private final List<Element> comment
    private final List<ExtraAttributeDoc> additionalValues
    private final PropertyMetaData metaData
    private final ClassMetaData referringClass

    PropertyDoc(PropertyMetaData propertyMetaData, List<Element> comment, List<ExtraAttributeDoc> additionalValues) {
        this(propertyMetaData.ownerClass, propertyMetaData, comment, additionalValues)
    }

    PropertyDoc(ClassMetaData referringClass, PropertyMetaData propertyMetaData, List<Element> comment, List<ExtraAttributeDoc> additionalValues) {
        name = propertyMetaData.name
        this.referringClass = referringClass
        this.metaData = propertyMetaData
        id = "${referringClass.className}:$name"
        this.comment = comment
        if (additionalValues == null) {
            throw new NullPointerException("additionalValues constructor var is null for referringClass: $referringClass")
        }
        this.additionalValues = additionalValues
    }

    PropertyDoc forClass(ClassDoc referringClass) {
        return forClass(referringClass, [])
    }

    PropertyDoc forClass(ClassDoc referringClass, Collection<ExtraAttributeDoc> additionalValues) {
        def refererMetaData = referringClass.classMetaData
        if (refererMetaData == this.referringClass && additionalValues.isEmpty()) {
            return this
        }
        return new PropertyDoc(refererMetaData, metaData, comment, additionalValues as List)
    }

    String getId() {
        return id
    }

    String getName() {
        return name
    }

    PropertyMetaData getMetaData() {
        return metaData
    }

    boolean isDeprecated() {
        return metaData.deprecated && !referringClass.deprecated
    }

    boolean isIncubating() {
        return metaData.incubating || metaData.ownerClass.incubating
    }

    boolean isReplaced() {
        return metaData.replaced
    }

    @Override
    String getReplacement() {
        return metaData.replacement
    }

    Element getDescription() {
        return comment.find { it.nodeName == 'para' }
    }

    List<Element> getComment() {
        return comment
    }

    List<ExtraAttributeDoc> getAdditionalValues() {
        return additionalValues
    }
}


