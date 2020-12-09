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

package gradlebuild.docs.dsl.docbook;

import gradlebuild.docs.dsl.docbook.model.ClassDoc;
import gradlebuild.docs.dsl.docbook.model.PropertyDoc;

public class ReferencedTypeBuilder {
    private final DslDocModel model;

    public ReferencedTypeBuilder(DslDocModel model) {
        this.model = model;
    }

    /**
     * Builds the docs for types referenced by properties and methods of the given class.
     */
    public void build(ClassDoc classDoc) {
        for (PropertyDoc propertyDoc : classDoc.getClassProperties()) {
            String referencedType = propertyDoc.getMetaData().getType().getName();
            if (!referencedType.equals(classDoc.getName())) {
                model.findClassDoc(referencedType);
            }
        }
    }
}
