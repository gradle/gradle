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

import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData
import org.w3c.dom.Element

class MethodDoc implements DslElementDoc {
    private final String id
    private final MethodMetaData metaData
    private final List<Element> comment
    private final ClassMetaData referringClass

    MethodDoc(MethodMetaData metaData, List<Element> comment) {
        this(metaData.ownerClass, metaData, comment)
    }

    MethodDoc(ClassMetaData referringClass, MethodMetaData metaData, List<Element> comment) {
        this.metaData = metaData
        this.referringClass = referringClass
        id = "$referringClass.className:$metaData.overrideSignature"
        this.comment = comment
    }

    MethodDoc forClass(ClassDoc referringClass) {
        def refererMetaData = referringClass.classMetaData
        if (refererMetaData == this.referringClass) {
            return this
        }
        return new MethodDoc(refererMetaData, metaData, comment)
    }

    String getId() {
        return id
    }

    String getName() {
        return metaData.name
    }

    MethodMetaData getMetaData() {
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
}
