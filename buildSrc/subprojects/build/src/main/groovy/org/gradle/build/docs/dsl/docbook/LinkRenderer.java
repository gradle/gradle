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
package org.gradle.build.docs.dsl.docbook;

import groovy.lang.GroovySystem;
import org.apache.commons.lang.StringUtils;
import org.gradle.build.docs.dsl.source.model.EnumConstantMetaData;
import org.gradle.build.docs.dsl.source.model.MethodMetaData;
import org.gradle.build.docs.dsl.source.model.TypeMetaData;
import org.gradle.internal.jvm.Jvm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Set;

public class LinkRenderer {
    private final Document document;
    private final DslDocModel model;
    private final Set<String> primitiveTypes = new HashSet<String>();
    private final String groovyVersion;
    private final String javaVersion;

    public LinkRenderer(Document document, DslDocModel model, String groovyVersion, String javaVersion) {
        this.document = document;
        this.model = model;
        this.groovyVersion = groovyVersion;
        this.javaVersion = javaVersion;
        primitiveTypes.add("boolean");
        primitiveTypes.add("byte");
        primitiveTypes.add("short");
        primitiveTypes.add("int");
        primitiveTypes.add("long");
        primitiveTypes.add("char");
        primitiveTypes.add("float");
        primitiveTypes.add("double");
        primitiveTypes.add("void");
    }

    public LinkRenderer(Document document, DslDocModel model) {
        this(document, model, GroovySystem.getVersion(), Jvm.current().getJavaVersion().getMajorVersion());
    }

    Node link(TypeMetaData type, final GenerationListener listener) {
        final Element linkElement = document.createElement("classname");

        type.visitSignature(new TypeMetaData.SignatureVisitor() {
            @Override
            public void visitText(String text) {
                linkElement.appendChild(document.createTextNode(text));
            }

            @Override
            public void visitType(String name) {
                linkElement.appendChild(addType(name, listener));
            }
        });

        linkElement.normalize();
        if (linkElement.getChildNodes().getLength() == 1 && linkElement.getFirstChild() instanceof Element) {
            return linkElement.getFirstChild();
        }
        return linkElement;
    }

    private Node addType(String className, GenerationListener listener) {
        if (model.isKnownType(className)) {
            Element linkElement = document.createElement("apilink");
            linkElement.setAttribute("class", className);
            return linkElement;
        }

        if (primitiveTypes.contains(className)) {
            Element classNameElement = document.createElement("classname");
            classNameElement.appendChild(document.createTextNode(className));
            return classNameElement;
        }

        if (className.startsWith("java.")) {
            Element linkElement = document.createElement("ulink");
            linkElement.setAttribute("url", String.format("http://download.oracle.com/javase/%s/docs/api/%s.html", javaVersion,
                    className.replace(".", "/")));
            Element classNameElement = document.createElement("classname");
            classNameElement.appendChild(document.createTextNode(StringUtils.substringAfterLast(className, ".")));
            linkElement.appendChild(classNameElement);
            return linkElement;
        }

        if (className.startsWith("groovy.")) {
            Element linkElement = document.createElement("ulink");
            linkElement.setAttribute("url", String.format("http://docs.groovy-lang.org/%s/html/gapi/%s.html", groovyVersion, className.replace(
                    ".", "/")));
            Element classNameElement = document.createElement("classname");
            classNameElement.appendChild(document.createTextNode(StringUtils.substringAfterLast(className, ".")));
            linkElement.appendChild(classNameElement);
            return linkElement;
        }

        //this if is a bit cheesy but 1-letter classname surely means a generic type and the warning will be useless
        if (className.length() > 1) {
            listener.warning(String.format("Could not generate link for unknown class '%s'", className));
        }
        Element element = document.createElement("classname");
        element.appendChild(document.createTextNode(className));
        return element;
    }

    public Node link(MethodMetaData method, GenerationListener listener) {
        if (model.isKnownType(method.getOwnerClass().getClassName())) {
            Element apilink = document.createElement("apilink");
            apilink.setAttribute("class", method.getOwnerClass().getClassName());
            apilink.setAttribute("method", method.getOverrideSignature());
            return apilink;
        } else {
            listener.warning(String.format("Could not generate link for method %s", method));
            Element element = document.createElement("UNKNOWN-METHOD");
            element.appendChild(document.createTextNode(String.format("%s.%s()", method.getOwnerClass().getClassName(),
                    method.getName())));
            return element;
        }
    }

    public Node link(EnumConstantMetaData enumConstant, GenerationListener listener) {
        if (model.isKnownType(enumConstant.getOwnerClass().getClassName())) {
            Element apilink = document.createElement("apilink");
            apilink.setAttribute("class", enumConstant.getOwnerClass().getClassName());
            apilink.setAttribute("method", enumConstant.getName());
            return apilink;
        } else {
            listener.warning(String.format("Could not generate link for enum constant %s", enumConstant));
            Element element = document.createElement("UNKNOWN-ENUM");
            element.appendChild(document.createTextNode(enumConstant.toString()));
            return element;
        }
    }
}
