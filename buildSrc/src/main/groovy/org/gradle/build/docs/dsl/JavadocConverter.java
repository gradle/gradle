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
package org.gradle.build.docs.dsl;

import org.gradle.util.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a raw javadoc comment into docbook.
 */
public class JavadocConverter {
    public final Pattern headerPattern = Pattern.compile("h(\\d)", Pattern.CASE_INSENSITIVE);
    public final Pattern endFirstSentencePattern = Pattern.compile("(\\.\\s+)|$", Pattern.MULTILINE);
    private Document document;

    public JavadocConverter(Document document) {
        this.document = document;
    }

    DocComment parse(final String rawCommentText) {
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(rawCommentText));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceFirst("\\s*\\*\\s*", "");
                if (line.startsWith("@")) {
                    // Ignore the tag section of the comment
                    break;
                }
                builder.append(line);
                builder.append("\n");
            }
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }

        String normalisedCommentText = builder.toString().trim();
        String firstSentence = firstSentence(normalisedCommentText);
        final List<Node> firstSentenceNodes = textToDom(firstSentence);
        final List<Node> nodes = textToDom(normalisedCommentText);

        return new DocComment() {
            public Iterable<? extends Node> getDocbook() {
                return nodes;
            }

            public Iterable<? extends Node> getFirstSentence() {
                return firstSentenceNodes;
            }
        };
    }

    private String firstSentence(String normalisedCommentText) {
        Matcher matcher = endFirstSentencePattern.matcher(normalisedCommentText);
        matcher.find();
        return normalisedCommentText.substring(0, matcher.end()).trim();
    }

    private List<Node> textToDom(String text) {
        Lexer lexer = new Lexer(text);
        final NodeStack nodes = new NodeStack();
        int sectionDepth = 0;
        StringBuilder link = null;

        while (lexer.next()) {
            switch (lexer.token) {
                case Text:
                    if (link != null) {
                        link.append(lexer.value);
                        break;
                    }
                    nodes.appendChild(lexer.value);
                    break;
                case Start:
                    if (lexer.value.equalsIgnoreCase("p")) {
                        nodes.push(lexer.value, document.createElement("para"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("code")) {
                        nodes.push(lexer.value, document.createElement("literal"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("pre")) {
                        nodes.push(lexer.value, document.createElement("programlisting"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("em")) {
                        nodes.push(lexer.value, document.createElement("emphasis"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("ul")) {
                        nodes.push(lexer.value, document.createElement("itemizedlist"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("li")) {
                        nodes.push(lexer.value, document.createElement("listitem"));
                        break;
                    }
                    if (lexer.value.equalsIgnoreCase("link")) {
                        nodes.push(lexer.value, document.createElement("apilink"));
                        link = new StringBuilder();
                        break;
                    }
                    Matcher matcher = headerPattern.matcher(lexer.value);
                    if (matcher.matches()) {
                        int depth = Integer.parseInt(matcher.group(1));
                        if (sectionDepth == 0) {
                            sectionDepth = depth - 1;
                        }
                        while (sectionDepth >= depth) {
                            nodes.pop("section");
                            sectionDepth--;
                        }
                        Element section = document.createElement("section");
                        while (sectionDepth < depth) {
                            nodes.push("section", section);
                            sectionDepth++;
                        }
                        nodes.push("title", document.createElement("title"));
                        sectionDepth = depth;
                        break;
                    }
                    nodes.appendChild(String.format("!!UNKNOWN TAG %s!!", lexer.value));
                    break;
                case End:
                    if (lexer.value.equalsIgnoreCase("link")) {
                        nodes.pop("link").setAttribute("class", link.toString());
                        link = null;
                        break;
                    }
                    Matcher endMatcher = headerPattern.matcher(lexer.value);
                    if (endMatcher.matches()) {
                        nodes.pop("title");
                        break;
                    }
                    nodes.pop(lexer.value);
            }
        }
        return nodes.nodes;
    }

    private class NodeStack {
        final List<Node> nodes = new ArrayList<Node>();
        final LinkedList<Element> stack = new LinkedList<Element>();
        final LinkedList<String> tags = new LinkedList<String>();

        public void appendChild(String text) {
            if (stack.isEmpty()) {
                if (text.trim().length() == 0) {
                    return;
                }
                Element para = document.createElement("para");
                nodes.add(para);
                stack.addFirst(para);
                tags.addFirst("");
            }
            stack.getFirst().appendChild(document.createTextNode(text));
        }

        public void push(String tag, Element element) {
            if (stack.isEmpty()) {
                nodes.add(element);
            } else {
                stack.getFirst().appendChild(element);
            }
            stack.addFirst(element);
            tags.addFirst(tag);
        }

        public Element pop(String tag) {
            Element result = null;
            if (!tags.isEmpty() && tags.getFirst().equalsIgnoreCase(tag)) {
                result = stack.removeFirst();
                tags.removeFirst();
            }
            return result;
        }
    }

    private static class Lexer {
        enum Token {
            Start, Text, End
        }

        final String input;
        int pos;
        Token token;
        String value;
        String inlineTag;

        private Lexer(String input) {
            this.input = input;
            pos = 0;
        }

        boolean next() {
            int remaining = input.length() - pos;
            if (remaining == 0) {
                token = null;
                value = null;
                return false;
            }

            int startNext = pos;
            while (remaining > 0) {
                if (inlineTag == null && remaining >= 3 && input.charAt(pos) == '<') {
                    break;
                }
                if (remaining >= 4 && input.startsWith("{@", pos)) {
                    break;
                }
                if (inlineTag != null && input.charAt(pos) == '}') {
                    break;
                }
                pos++;
                remaining--;
            }

            if (pos > startNext) {
                token = Token.Text;
                value = input.substring(startNext, pos);

                return true;
            }

            if (inlineTag == null && remaining >= 3 && input.charAt(pos) == '<') {
                pos++;
                token = Token.Start;
                if (input.charAt(pos) == '/') {
                    token = Token.End;
                    pos++;
                }
                int endpos = input.indexOf('>', pos);
                value = input.substring(pos, endpos);
                pos = endpos + 1;
                return true;
            }

            if (remaining >= 4 && input.startsWith("{@", pos)) {
                pos += 2;
                int endpos = pos;
                while (endpos < input.length() && !Character.isWhitespace(input.charAt(endpos))) {
                    endpos++;
                }
                token = Token.Start;
                value = input.substring(pos, endpos);
                inlineTag = value;
                while (endpos < input.length() && Character.isWhitespace(input.charAt(endpos))) {
                    endpos++;
                }
                pos = endpos;
                return true;
            }

            if (inlineTag != null && input.charAt(pos) == '}') {
                token = Token.End;
                value = inlineTag;
                inlineTag = null;
                pos = pos + 1;
                return true;
            }

            throw new IllegalStateException(); 
        }
    }

}
