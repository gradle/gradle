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
import gradlebuild.docs.dsl.source.model.ClassMetaData;

import java.util.List;

public class ClassDocSuperTypeBuilder {
    private final DslDocModel model;
    private final GenerationListener listener;

    public ClassDocSuperTypeBuilder(DslDocModel model, GenerationListener listener) {
        this.model = model;
        this.listener = listener;
    }

    /**
     * Builds and attaches the supertypes of the given class
     */
    void build(ClassDoc classDoc) {
        ClassMetaData classMetaData = classDoc.getClassMetaData();
        String superClassName = classMetaData.getSuperClassName();
        if (superClassName != null) {
            // Assume this is a class and so has implemented all properties and methods somewhere in the superclass hierarchy
            ClassDoc superClass = model.getClassDoc(superClassName);
            classDoc.setSuperClass(superClass);
            superClass.addSubClass(classDoc);
        }

        List<String> interfaceNames = classMetaData.getInterfaceNames();
        for (String interfaceName : interfaceNames) {
            ClassDoc superInterface = model.findClassDoc(interfaceName);
            if (superInterface != null) {
                classDoc.getInterfaces().add(superInterface);
                superInterface.addSubClass(classDoc);
            }
        }

    }
}
