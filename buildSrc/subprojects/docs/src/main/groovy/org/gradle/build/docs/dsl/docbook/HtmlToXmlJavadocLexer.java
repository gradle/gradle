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

import java.util.*;

/**
 * Normalises and cleans up HTML to convert it to XML semantics.
 */
public class HtmlToXmlJavadocLexer implements JavadocLexer {
    private final JavadocLexer lexer;
    private final Set<String> blockElements = new HashSet<String>();
    private final Set<String> blockContent = new HashSet<String>();

    public HtmlToXmlJavadocLexer(JavadocLexer lexer) {
        this.lexer = lexer;
        blockElements.add("p");
        blockElements.add("pre");
        blockElements.add("ul");
        blockElements.add("ol");
        blockElements.add("li");
        blockElements.add("h1");
        blockElements.add("h2");
        blockElements.add("h3");
        blockElements.add("h4");
        blockElements.add("h5");
        blockElements.add("h6");
        blockElements.add("table");
        blockElements.add("thead");
        blockElements.add("tbody");
        blockElements.add("tr");
        blockElements.add("dl");
        blockElements.add("dt");
        blockElements.add("dd");

        blockContent.add("ul");
        blockContent.add("ol");
        blockContent.add("table");
        blockContent.add("thead");
        blockContent.add("tbody");
        blockContent.add("tr");
        blockContent.add("dl");
    }

    @Override
    public void visit(TokenVisitor visitor) {
        lexer.visit(new VisitorImpl(visitor));
    }

    private class VisitorImpl extends TokenVisitor {
        private final TokenVisitor visitor;
        private final LinkedList<String> elementStack = new LinkedList<String>();
        private final Map<String, String> attributes = new HashMap<String, String>();

        public VisitorImpl(TokenVisitor visitor) {
            this.visitor = visitor;
        }

        private void unwindTo(String element, TokenVisitor visitor) {
            if (elementStack.contains(element)) {
                while (!elementStack.getFirst().equals(element)) {
                    visitor.onEndHtmlElement(elementStack.removeFirst());
                }
                elementStack.removeFirst();
                visitor.onEndHtmlElement(element);
            }
        }

        private void unwindTo(Collection<String> ancestors, TokenVisitor visitor) {
            for (int i = 0; i < elementStack.size(); i++) {
                if (ancestors.contains(elementStack.get(i))) {
                    for (; i > 0; i--) {
                        visitor.onEndHtmlElement(elementStack.removeFirst());
                    }
                    break;
                }
            }
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
            if (name.equals("li")) {
                unwindTo(Arrays.asList("ul", "ol"), visitor);
            } else if (name.equals("dt") || name.endsWith("dd")) {
                unwindTo(Arrays.asList("dl"), visitor);
            } else if (name.equals("tr")) {
                unwindTo(Arrays.asList("table", "thead", "tbody"), visitor);
            } else if (name.equals("th") || name.endsWith("td")) {
                unwindTo(Arrays.asList("tr", "table", "thead", "tbody"), visitor);
            } else if (blockElements.contains(name)) {
                unwindTo("p", visitor);
            } else if (!blockContent.contains(name) && !(name.equals("a") && attributes.containsKey("name"))) {
                onInlineContent();
            }

            elementStack.addFirst(name);
            visitor.onStartHtmlElement(name);
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                visitor.onHtmlElementAttribute(entry.getKey(), entry.getValue());
            }
            attributes.clear();
            visitor.onStartHtmlElementComplete(name);
        }

        @Override
        public void onEndHtmlElement(String name) {
            unwindTo(name, visitor);
        }

        private void onInlineContent() {
            if (elementStack.isEmpty() || blockContent.contains(elementStack.getFirst())) {
                elementStack.addFirst("p");
                visitor.onStartHtmlElement("p");
                visitor.onStartHtmlElementComplete("p");
            }
        }

        @Override
        public void onStartJavadocTag(String name) {
            onInlineContent();
            visitor.onStartJavadocTag(name);
        }

        @Override
        public void onEndJavadocTag(String name) {
            onInlineContent();
            visitor.onEndJavadocTag(name);
        }

        @Override
        public void onText(String text) {
            boolean ws = text.matches("\\s*");
            if (!ws) {
                onInlineContent();
                visitor.onText(text);
            } else if (!elementStack.isEmpty() && !blockContent.contains(elementStack.getFirst())) {
                visitor.onText(text);
            }
        }

        @Override
        void onEnd() {
            while (!elementStack.isEmpty()) {
                visitor.onEndHtmlElement(elementStack.removeFirst());
            }
            visitor.onEnd();
        }
    }
}
