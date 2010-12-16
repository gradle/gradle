/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.docbook

import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.w3c.dom.Element
import org.gradle.build.docs.dsl.model.ClassMetaData

class PropertyDoc {
    private final String id
    private final String name
    private final List<Element> comment
    private final List<ExtraAttributeDoc> additionalValues
    private final PropertyMetaData metaData

    PropertyDoc(PropertyMetaData propertyMetaData, List<Element> comment, List<ExtraAttributeDoc> additionalValues) {
        this(propertyMetaData.ownerClass, propertyMetaData, comment, additionalValues)
    }

    PropertyDoc(ClassMetaData referringClass, PropertyMetaData propertyMetaData, List<Element> comment, List<ExtraAttributeDoc> additionalValues) {
        name = propertyMetaData.name
        this.metaData = propertyMetaData
        id = "${referringClass.className}:$name"
        this.comment = comment
        this.additionalValues = additionalValues
    }

    PropertyDoc forClass(ClassMetaData classMetaData, List<ExtraAttributeDoc> additionalValues) {
        return new PropertyDoc(classMetaData, metaData, comment, additionalValues)
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


