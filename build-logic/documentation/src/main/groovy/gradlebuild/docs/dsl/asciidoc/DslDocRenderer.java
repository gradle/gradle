/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.docs.dsl.asciidoc;

import gradlebuild.docs.dsl.docbook.BasicJavadocLexer;
import gradlebuild.docs.dsl.docbook.HtmlToXmlJavadocLexer;
import gradlebuild.docs.dsl.docbook.JavadocLexer;
import gradlebuild.docs.dsl.docbook.JavadocScanner;
import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.PropertyMetaData;
import groovy.lang.Closure;
import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DslDocRenderer {
    public DslDocRenderer() {
//        memberRenderers.add(new PropertiesRenderer(linkRenderer, listener));
//        memberRenderers.add(new MethodsRenderer(linkRenderer, listener));
//        memberRenderers.add(new BlocksRenderer(linkRenderer, listener));
    }

    public void mergeContent(ClassMetaData classMetaData, Writer parent) throws IOException, ClassNotFoundException {
        Template template = new SimpleTemplateEngine().createTemplate(getClass().getResource("class_template.adoc"));
        Map<Object, Object> options = new HashMap<>();
        options.put("classMetadata", classMetaData);
        List<PropertyMetaData> declaredProperties = new ArrayList<>(classMetaData.getDeclaredProperties());
        declaredProperties.sort(Comparator.comparing(PropertyMetaData::getName));
        Set<MethodMetaData> gettersAndSetters = classMetaData.getDeclaredProperties().stream()
            .flatMap(property -> Stream.of(property.getGetter(), property.getSetter()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<MethodMetaData> declaredMethods = classMetaData.getDeclaredMethods()
            .stream()
            .filter(method -> !gettersAndSetters.contains(method)).sorted(Comparator.comparing(MethodMetaData::getName)).collect(Collectors.toList());
        options.put("declaredMethods", declaredMethods);
        options.put("declaredProperties", declaredProperties);
        options.put("parseJavadoc", new Closure<String>(this) {
            public String doCall(String arg) {
                return parseJavadoc(arg);
            }
        });
        options.put("parseJavadocDescription", new Closure<String>(this) {
            public String doCall(String arg) {
                return parseJavadocDescription(arg);
            }
        });
        Writable result = template.make(options);
        result.writeTo(parent);
    }

    private String parseJavadoc(String rawJavadoc) {
        JavadocLexer lexer = new HtmlToXmlJavadocLexer(new BasicJavadocLexer(new JavadocScanner(rawJavadoc)));
        StringBuilder builder = new StringBuilder();
        AsciidocGeneratingTokenVisitor visitor = new AsciidocGeneratingTokenVisitor(builder);
        visitor.addHandler(new PreHandler(builder));
        UlHandler ulHandler = new UlHandler(builder);
        visitor.addHandler(ulHandler);
        visitor.addHandler(new LiHandler(ulHandler, builder));
        lexer.visit(visitor);
        return builder.toString().trim();
    }

    private String parseJavadocDescription(String rawJavadoc) {
        JavadocLexer lexer = new HtmlToXmlJavadocLexer(new BasicJavadocLexer(new JavadocScanner(rawJavadoc)));
        StringBuilder builder = new StringBuilder();
        AsciidocGeneratingTokenVisitor visitor = new AsciidocGeneratingTokenVisitor(new StringBuilder());
        visitor.addHandler(new FirstParagraphHandler(builder));
        lexer.visit(visitor);
        return builder.toString().trim();
    }

    interface HtmlElementHandler {
        boolean onStartElement(String element, Map<String, String> attributes);

        void onText(String text);

        void onEndElement(String element);
    }

    static class AsciidocGeneratingTokenVisitor extends JavadocLexer.TokenVisitor {
        private final StringBuilder builder;
        private final List<HtmlElementHandler> elementHandlers = new ArrayList<>();
        private final Map<String, String> attributes = new HashMap<>();
        private final LinkedList<String> tagStack = new LinkedList<>();
        private final LinkedList<HtmlElementHandler> handlerStack = new LinkedList<>();

        public AsciidocGeneratingTokenVisitor(StringBuilder builder) {
            this.builder = builder;
        }

        public void onText(String text) {
            if (!handlerStack.isEmpty()) {
                handlerStack.getFirst().onText(text);
                return;
            }
            builder.append(text.lines().map(String::trim).collect(Collectors.joining("\n")));
        }

        public void addHandler(HtmlElementHandler handler) {
            elementHandlers.add(handler);
        }

        @Override
        public void onStartHtmlElement(String name) {
            attributes.clear();
        }

        @Override
        public void onHtmlElementAttribute(String name, String value) {
            attributes.put(name, value);
        }

        @Override
        public void onStartHtmlElementComplete(String name) {
            for (HtmlElementHandler handler : elementHandlers) {
                if (handler.onStartElement(name, attributes)) {
                    handlerStack.addFirst(handler);
                    tagStack.addFirst(name);
                    return;
                }
            }
//            throw new UnsupportedOperationException();
        }

        @Override
        public void onEndHtmlElement(String name) {
            if (!tagStack.isEmpty() && tagStack.getFirst().equals(name)) {
                tagStack.removeFirst();
                handlerStack.removeFirst().onEndElement(name);
            }
        }
    }

    static class PreHandler implements HtmlElementHandler {
        private final StringBuilder builder;

        public PreHandler(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            if (!element.equals("pre")) {
                return false;
            }
            appendNewlineIfNecessary(builder);
            builder.append("\n[source,java]\n----\n");
            return true;
        }

        @Override
        public void onText(String text) {
            builder.append(text);
        }

        @Override
        public void onEndElement(String element) {
            appendNewlineIfNecessary(builder);
            builder.append("----\n\n");
        }
    }

    static class UlHandler implements HtmlElementHandler {
        private final StringBuilder builder;
        private int level;

        public UlHandler(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            if (!element.equals("ul")) {
                return false;
            }
            appendNewlineIfNecessary(builder);
            if (level == 0) {
                builder.append("\n");
            }
            level++;
            return true;
        }

        @Override
        public void onText(String text) {
            // TODO: throw here
        }

        @Override
        public void onEndElement(String element) {
            level--;
            if (level == 0) {
                builder.append("\n");
            }
        }
    }

    private static void appendNewlineIfNecessary(StringBuilder builder) {
        if (builder.charAt(builder.length() - 1) != '\n') {
            builder.append("\n");
        }
    }

    static class LiHandler implements HtmlElementHandler {
        private final UlHandler ulHandler;
        private final StringBuilder builder;

        public LiHandler(UlHandler ulHandler, StringBuilder builder) {
            this.ulHandler = ulHandler;
            this.builder = builder;
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            if (!element.equals("li")) {
                return false;
            }
            appendNewlineIfNecessary(builder);
            for (int i = 0; i < ulHandler.level; i++) {
                builder.append("*");
            }
            builder.append(" ");
            return true;
        }

        @Override
        public void onText(String text) {
            builder.append(text);
        }

        @Override
        public void onEndElement(String element) {
            appendNewlineIfNecessary(builder);
        }
    }

    static class FirstParagraphHandler implements HtmlElementHandler {
        private final StringBuilder builder;
        boolean first = true;

        public FirstParagraphHandler(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            return element.equals("p");
        }

        @Override
        public void onText(String text) {
            if (first) {
                builder.append(text);
            }
        }

        @Override
        public void onEndElement(String element) {
            first = false;
        }
    }

//    void merge(ClassDoc classDoc, Element chapter) {
//        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
//            memberRenderer.renderSummaryTo(classDoc, chapter);
//        }
//        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
//            memberRenderer.renderDetailsTo(classDoc, chapter);
//        }
//    }
}
