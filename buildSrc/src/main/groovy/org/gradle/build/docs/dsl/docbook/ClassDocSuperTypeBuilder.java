/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ClassDoc;
import org.gradle.build.docs.dsl.source.model.ClassMetaData;

import java.util.ArrayList;
import java.util.List;

public class ClassDocSuperTypeBuilder {
    private final DslDocModel model;
    private final GenerationListener listener;

    public ClassDocSuperTypeBuilder(DslDocModel model, GenerationListener listener) {
        this.model = model;
        this.listener = listener;
    }

    /**
     * Builds and attaches the supertype of the given class
     */
    void build(ClassDoc classDoc) {
        ClassMetaData classMetaData = classDoc.getClassMetaData();
        String superClassName = classMetaData.getSuperClassName();
        if (superClassName != null) {
            // Assume this is a class and so has implemented all properties and methods somewhere in the superclass hierarchy
            ClassDoc superClass = model.getClassDoc(superClassName);
            classDoc.setSuperClass(superClass);
            return;
        }

        // Assume this is an interface - pick one interface to be the supertype
        // TODO - improve the property and methods builders to handle stuff inherited from multiple interfaces

        List<String> interfaceNames = classMetaData.getInterfaceNames();
        List<ClassDoc> candidates = new ArrayList<ClassDoc>();
        for (String interfaceName : interfaceNames) {
            ClassDoc superInterface = model.findClassDoc(interfaceName);
            if (superInterface != null) {
                candidates.add(superInterface);
            }
        }
        if (candidates.isEmpty()) {
            // No documented supertypes
            return;
        }

        ClassDoc superInterface = candidates.get(0);
        if (candidates.size() > 1) {
            listener.warning("Ignoring properties and methods inherited from interfaces " + candidates.subList(1, candidates.size()));
        }
        classDoc.setSuperClass(superInterface);
    }
}
