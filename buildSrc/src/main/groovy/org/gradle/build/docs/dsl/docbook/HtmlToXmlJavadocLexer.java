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

public class HtmlToXmlJavadocLexer implements JavadocLexer {
    private final JavadocLexer lexer;
    private final LinkedList<String> elementStack = new LinkedList<String>();
    private final Set<String> blockElements = new HashSet<String>();

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
    }

    public void visit(TokenVisitor visitor) {
        lexer.visit(new VisitorImpl(visitor));
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

    private class VisitorImpl extends TokenVisitor {
        private final TokenVisitor visitor;

        public VisitorImpl(TokenVisitor visitor) {
            this.visitor = visitor;
        }

        @Override
        public void onStartHtmlElement(String name) {
            if (name.equals("li")) {
                unwindTo("li", visitor);
            } else if (blockElements.contains(name)) {
                unwindTo("p", visitor);
            }
            elementStack.addFirst(name);
            visitor.onStartHtmlElement(name);
        }

        @Override
        public void onHtmlElementAttribute(String name, String value) {
            visitor.onHtmlElementAttribute(name, value);
        }

        @Override
        public void onStartHtmlElementComplete(String name) {
            visitor.onStartHtmlElementComplete(name);
        }

        @Override
        public void onEndHtmlElement(String name) {
            unwindTo(name, visitor);
            visitor.onEndHtmlElement(name);
        }

        @Override
        public void onStartJavadocTag(String name) {
            visitor.onStartJavadocTag(name);
        }

        @Override
        public void onEndJavadocTag(String name) {
            visitor.onEndJavadocTag(name);
        }

        @Override
        public void onText(String text) {
            visitor.onText(text);
        }
    }
}
