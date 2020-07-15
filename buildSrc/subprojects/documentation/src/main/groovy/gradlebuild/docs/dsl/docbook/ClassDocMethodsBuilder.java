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

import groovy.lang.Closure;
import gradlebuild.docs.dsl.docbook.model.BlockDoc;
import gradlebuild.docs.dsl.docbook.model.ClassDoc;
import gradlebuild.docs.dsl.docbook.model.MethodDoc;
import gradlebuild.docs.dsl.docbook.model.PropertyDoc;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.TypeMetaData;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassDocMethodsBuilder extends ModelBuilderSupport {
    private final JavadocConverter javadocConverter;
    private final GenerationListener listener;

    public ClassDocMethodsBuilder(JavadocConverter converter, GenerationListener listener) {
        this.javadocConverter = converter;
        this.listener = listener;
    }

    /**
     * Builds the methods and script blocks of the given class. Assumes properties have already been built.
     */
    public void build(ClassDoc classDoc) {
        Set<String> signatures = new HashSet<String>();

        for (Element tr : children(classDoc.getMethodsTable(), "tr")) {
            List<Element> cells = children(tr, "td");
            if (cells.size() != 1) {
                throw new RuntimeException(String.format("Expected 1 cell in <tr>, found: %s", tr));
            }
            String methodName = cells.get(0).getTextContent().trim();
            Collection<MethodMetaData> methods = classDoc.getClassMetaData().findDeclaredMethods(methodName);
            if (methods.isEmpty()) {
                throw new RuntimeException(String.format("No metadata for method '%s.%s()'. Available methods: %s", classDoc.getName(), methodName, classDoc.getClassMetaData().getDeclaredMethodNames()));
            }
            for (MethodMetaData method : methods) {
                DocComment docComment = javadocConverter.parse(method, listener);
                MethodDoc methodDoc = new MethodDoc(method, docComment.getDocbook());
                if (methodDoc.getDescription() == null) {
                    throw new RuntimeException(String.format("Docbook content for '%s %s' does not contain a description paragraph.", classDoc.getName(), method.getSignature()));
                }
                PropertyDoc property = classDoc.findProperty(methodName);
                boolean multiValued = false;
                if (property != null && method.getParameters().size() == 1 && method.getParameters().get(0).getType().getSignature().equals(Closure.class.getName())) {
                    TypeMetaData type = property.getMetaData().getType();
                    if (type.getName().equals("java.util.List")
                            || type.getName().equals("java.util.Collection")
                            || type.getName().equals("java.util.Set")
                            || type.getName().equals("java.util.Iterable")) {
                        type = type.getTypeArgs().get(0);
                        multiValued = true;
                    }
                    classDoc.addClassBlock(new BlockDoc(methodDoc, property, type, multiValued));
                } else {
                    classDoc.addClassMethod(methodDoc);
                    signatures.add(method.getOverrideSignature());
                }
            }
        }

        for (ClassDoc supertype : classDoc.getSuperTypes()) {
            for (MethodDoc method: supertype.getClassMethods()){
                if (signatures.add(method.getMetaData().getOverrideSignature())) {
                    classDoc.addClassMethod(method);
                }
            }
        }
    }
}
