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

import gradlebuild.docs.dsl.source.TypeNameResolver;
import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.TypeMetaData;
import gradlebuild.docs.model.ClassMetaDataRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a javadoc link into docbook.
 */
public class JavadocLinkConverter {
    private static final Pattern LINK_PATTERN = Pattern.compile("(?s)\\s*([\\w\\.]*)(#(\\w+)(\\((.*)\\))?)?.*");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(\\w+)\\s*(.*?)\\s*");
    private static final Pattern PARAM_DELIMITER = Pattern.compile(",\\s*");
    private final Document document;
    private final TypeNameResolver typeNameResolver;
    private final LinkRenderer linkRenderer;
    private final ClassMetaDataRepository<ClassMetaData> repository;

    public JavadocLinkConverter(Document document, TypeNameResolver typeNameResolver, LinkRenderer linkRenderer,
                                ClassMetaDataRepository<ClassMetaData> repository) {
        this.document = document;
        this.typeNameResolver = typeNameResolver;
        this.linkRenderer = linkRenderer;
        this.repository = repository;
    }

    /**
     * Converts a javadoc link into docbook.
     */
    public Node resolve(String link, ClassMetaData classMetaData, GenerationListener listener) {
        Node node = doResolve(link, classMetaData, listener);
        if (node != null) {
            return node;
        }

        listener.warning(String.format("Could not convert Javadoc link '%s'", link));
        Element element = document.createElement("UNHANDLED-LINK");
        element.appendChild(document.createTextNode(link));
        return element;
    }

    private Node doResolve(String link, ClassMetaData classMetaData, GenerationListener listener) {
        Matcher matcher = LINK_PATTERN.matcher(link);
        if (!matcher.matches()) {
            return null;
        }

        String className = null;
        if (matcher.group(1).length() > 0) {
            className = typeNameResolver.resolve(matcher.group(1), classMetaData);
            if (className == null) {
                return null;
            }
        }
        if (matcher.group(2) == null) {
            return linkRenderer.link(new TypeMetaData(className), listener);
        }

        ClassMetaData targetClass;
        if (className != null) {
            targetClass = repository.find(className);
            if (targetClass == null) {
                return null;
            }
        } else {
            targetClass = classMetaData;
        }

        String methodSignature = matcher.group(3);
        if (matcher.group(5) != null) {
            StringBuilder signature = new StringBuilder();
            signature.append(methodSignature);
            signature.append("(");
            if (matcher.group(5).length() > 0) {
                String[] types = PARAM_DELIMITER.split(matcher.group(5));
                for (int i = 0; i < types.length; i++) {
                    String type = types[i];
                    Matcher typeMatcher = TYPE_PATTERN.matcher(type);
                    if (!typeMatcher.matches()) {
                        return null;
                    }
                    if (i > 0) {
                        signature.append(", ");
                    }
                    signature.append(typeNameResolver.resolve(typeMatcher.group(1), classMetaData));
                    String suffix = typeMatcher.group(2);
                    if (suffix.equals("...")) {
                        suffix = "[]";
                    }
                    signature.append(suffix);
                }
            }
            signature.append(")");
            methodSignature = signature.toString();
        }

        if (targetClass.isEnum() && targetClass.getEnumConstant(methodSignature) != null) {
            return linkRenderer.link(targetClass.getEnumConstant(methodSignature), listener);
        }

        MethodMetaData method = findMethod(methodSignature, targetClass);
        if (method == null) {
            return null;
        }

        return linkRenderer.link(method, listener);
    }

    private MethodMetaData findMethod(String name, ClassMetaData targetClass) {
        List<MethodMetaData> candidates = new ArrayList<MethodMetaData>();
        for (MethodMetaData methodMetaData : targetClass.getDeclaredMethods()) {
            if (name.equals(methodMetaData.getOverrideSignature())) {
                return methodMetaData;
            }
            if (name.equals(methodMetaData.getName())) {
                candidates.add(methodMetaData);
            }
        }

        if (candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    /**
     * Converts a javadoc value link into docbook.
     */
    public Node resolveValue(String fieldName, ClassMetaData classMetaData, GenerationListener listener) {
        String[] parts = fieldName.split("#");
        ClassMetaData targetClass;
        if (parts[0].length() > 0) {
            String targetClassName = typeNameResolver.resolve(parts[0], classMetaData);
            targetClass = repository.find(targetClassName);
            if (targetClass == null) {
                listener.warning(String.format("Could not locate target class '%s' for field value link '%s'", targetClass, fieldName));
                Element element = document.createElement("UNHANDLED-VALUE");
                element.appendChild(document.createTextNode(targetClassName + ":" + parts[1]));
                return element;
            }
        } else {
            targetClass = classMetaData;
        }

        String value = targetClass.getConstants().get(parts[1]);
        if (value == null) {
            listener.warning(String.format("Field '%s' does not have any value", fieldName));
            Element element = document.createElement("NO-VALUE-FOR_FIELD");
            element.appendChild(document.createTextNode(targetClass.getClassName() + ":" + parts[1]));
            return element;
        }

        return createLiteralNode(value);
    }

    private Node createLiteralNode(String value) {
        Element element = document.createElement("literal");
        element.appendChild(document.createTextNode(value));
        return element;
    }
}
