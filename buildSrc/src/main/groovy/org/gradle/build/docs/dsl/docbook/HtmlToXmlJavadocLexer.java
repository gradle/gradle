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
    private final LinkedList<Token> pendingTokens = new LinkedList<Token>();

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

    public Token getToken() {
        return pendingTokens.getFirst();
    }

    public boolean next() {
        if (pendingTokens.size() > 0) {
            pendingTokens.removeFirst();
            if (pendingTokens.size() > 0) {
                return true;
            }
        }

        boolean next = lexer.next();
        if (!next) {
            return false;
        }

        Token token = lexer.getToken();
        switch (token.tokenType) {
            case StartElement:
                if (token.value.equals("li")) {
                    unwindTo("li");
                } else if (blockElements.contains(token.value)) {
                    unwindTo("p");
                }
                // FALL THROUGH
            case StartTag:
                elementStack.addFirst(token.value);
                break;
            case End:
                unwindTo(token.value);
                break;
            default:
        }

        pendingTokens.addLast(token);
        return true;
    }

    private void unwindTo(String element) {
        if (elementStack.contains(element)) {
            while (!elementStack.getFirst().equals(element)) {
                pendingTokens.add(new Token(TokenType.End, elementStack.removeFirst()));
            }
            elementStack.removeFirst();
            pendingTokens.add(new Token(TokenType.End, element));
        }
    }
}
