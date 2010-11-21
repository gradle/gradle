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
package org.gradle.build.docs.dsl.javadoc;

import org.gradle.api.GradleException;
import org.gradle.build.docs.dsl.DocComment;
import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.dsl.model.PropertyMetaData;
import org.gradle.util.GUtil;
import org.gradle.util.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts raw javadoc comments into docbook.
 */
public class JavadocConverter {
    private static final Pattern HEADER_PATTERN = Pattern.compile("h(\\d)", Pattern.CASE_INSENSITIVE);
    private final Document document;
    private final JavadocLinkConverter linkConverter;

    public JavadocConverter(Document document, JavadocLinkConverter linkConverter) {
        this.document = document;
        this.linkConverter = linkConverter;
    }

    public DocComment parse(final String rawCommentText, ClassMetaData classMetaData) {
        CommentSource commentSource = new CommentSource() {
            public String getCommentText() {
                throw new UnsupportedOperationException();
            }
        };

        try {
            return parse(rawCommentText, classMetaData, commentSource);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nComment: %s", classMetaData.getClassName(), rawCommentText), e);
        }
    }

    public DocComment parse(String rawCommentText, final PropertyMetaData propertyMetaData, ClassMetaData classMetaData) {
        CommentSource commentSource = new CommentSource() {
            public String getCommentText() {
                String comment = propertyMetaData.getInheritedRawCommentText();
                return GUtil.isTrue(comment) ? comment : "!!NO INHERITED DOC COMMENT!!";
            }
        };
        try {
            return parse(rawCommentText, classMetaData, commentSource);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nProperty: %s%nComment: %s", classMetaData.getClassName(), propertyMetaData.getName(), rawCommentText), e);
        }
    }

    private DocComment parse(String rawCommentText, ClassMetaData classMetaData, CommentSource inheritedCommentSource) {
        final List<Node> nodes = textToDom(rawCommentText, classMetaData, inheritedCommentSource);
        return new DocComment() {
            public Iterable<? extends Node> getDocbook() {
                return nodes;
            }
        };
    }

    private List<Node> textToDom(String rawCommentText, ClassMetaData classMetaData, CommentSource inheritedCommentSource) {
        Lexer lexer = new Lexer(new Scanner(rawCommentText));
        NodeStack nodes = new NodeStack();
        HtmlGeneratingTokenHandler handler = new HtmlGeneratingTokenHandler(nodes);
        handler.add(new TagToElementTranslatingTokenHandler(nodes, document));
        handler.add(new HeaderHandler(nodes, document));
        handler.add(new LinkHandler(nodes, linkConverter, classMetaData));
        handler.add(new InheritDocHandler(lexer, inheritedCommentSource));

        while (lexer.next()) {
            switch (lexer.token) {
                case Text:
                    handler.onText(lexer.value);
                    break;
                case Start:
                    handler.onStartTag(lexer.value);
                    break;
                case End:
                    handler.onEndTag(lexer.value);
                    break;
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

        public void appendChild(Node node) {
            if (stack.isEmpty()) {
                nodes.add(node);
            } else {
                stack.getFirst().appendChild(node);
            }
        }

        public void push(String tag, Element element) {
            appendChild(element);
            stack.addFirst(element);
            tags.addFirst(tag);
        }

        public Element pop(String tag) {
            Element result = null;
            if (!tags.isEmpty() && tags.getFirst().equals(tag)) {
                result = stack.removeFirst();
                tags.removeFirst();
            }
            return result;
        }
    }

    private class HtmlGeneratingTokenHandler {
        final NodeStack nodes;
        final List<TagHandler> handlers = new ArrayList<TagHandler>();
        final LinkedList<TagHandler> handlerStack = new LinkedList<TagHandler>();
        final LinkedList<String> tagStack = new LinkedList<String>();

        public HtmlGeneratingTokenHandler(NodeStack nodes) {
            this.nodes = nodes;
        }

        public void add(TagHandler handler) {
            handlers.add(handler);
        }

        public void onStartTag(String name) {
            for (TagHandler handler : handlers) {
                if (handler.onStartTag(name)) {
                    handlerStack.addFirst(handler);
                    tagStack.addFirst(name);
                    return;
                }
            }

            nodes.appendChild(String.format("!!UNKNOWN TAG %s!!", name));
        }

        public void onText(String text) {
            if (!handlerStack.isEmpty()) {
                handlerStack.getFirst().onText(text);
                return;
            }

            nodes.appendChild(text);
        }

        public void onEndTag(String name) {
            if (!tagStack.isEmpty() && tagStack.getFirst().equals(name)) {
                tagStack.removeFirst();
                handlerStack.removeFirst().onEndTag(name);
            }
        }
    }

    private interface TagHandler {
        boolean onStartTag(String tag);

        void onText(String text);

        void onEndTag(String tag);
    }

    private static class TagToElementTranslatingTokenHandler implements TagHandler {
        private final NodeStack nodes;
        private final Document document;
        private final Map<String, String> tagToElementMap = new HashMap<String, String>();

        private TagToElementTranslatingTokenHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            tagToElementMap.put("p", "para");
            tagToElementMap.put("code", "literal");
            tagToElementMap.put("pre", "programlisting");
            tagToElementMap.put("ul", "itemizedlist");
            tagToElementMap.put("li", "listitem");
            tagToElementMap.put("em", "emphasis");
        }

        public boolean onStartTag(String tag) {
            String element = tagToElementMap.get(tag);
            if (element == null) {
                return false;
            }
            nodes.push(tag, document.createElement(element));
            return true;
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }

        public void onEndTag(String tag) {
            nodes.pop(tag);
        }
    }

    private static class HeaderHandler implements TagHandler {
        final NodeStack nodes;
        final Document document;
        int sectionDepth;

        private HeaderHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public boolean onStartTag(String tag) {
            Matcher matcher = HEADER_PATTERN.matcher(tag);
            if (!matcher.matches()) {
                return false;
            }
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
            return true;
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }

        public void onEndTag(String tag) {
            nodes.pop("title");
        }
    }

    private static class LinkHandler implements TagHandler {
        private final NodeStack nodes;
        private final JavadocLinkConverter linkConverter;
        private final ClassMetaData classMetaData;
        private StringBuilder link;

        private LinkHandler(NodeStack nodes, JavadocLinkConverter linkConverter, ClassMetaData classMetaData) {
            this.nodes = nodes;
            this.linkConverter = linkConverter;
            this.classMetaData = classMetaData;
        }

        public boolean onStartTag(String tag) {
            if (!tag.equals("link")) {
                return false;
            }
            link = new StringBuilder();
            return true;
        }

        public void onText(String text) {
            link.append(text);
        }

        public void onEndTag(String tag) {
            for (Node node : linkConverter.resolve(link.toString(), classMetaData)) {
                nodes.appendChild(node);
            }
            link = null;
        }
    }

    private static class InheritDocHandler implements TagHandler {
        private final CommentSource source;
        private final Lexer lexer;

        private InheritDocHandler(Lexer lexer, CommentSource source) {
            this.lexer = lexer;
            this.source = source;
        }

        public boolean onStartTag(String tag) {
            return tag.equals("inheritDoc");
        }

        public void onText(String text) {
            // ignore
        }

        public void onEndTag(String tag) {
            lexer.pushText(source.getCommentText());
        }
    }

    private interface CommentSource {
        String getCommentText();
    }

    private static class Lexer {
        private static final Pattern END_TAG_NAME = Pattern.compile("\\s|}");
        private static final Pattern WHITESPACE = Pattern.compile("\\s+");
        private static final Pattern HTML_ELEMENT = Pattern.compile("<\\\\?.+?>");
        private static final Pattern HTML_ENTITY = Pattern.compile("&.+?;");
        private static final Pattern TAG = Pattern.compile("\\{@.+?\\}");
        private static final Map<String, String> ENTITIES = new HashMap<String, String>();

        static {
            ENTITIES.put("amp", "&");
            ENTITIES.put("lt", "<");
            ENTITIES.put("gt", ">");
        }

        enum Token {
            Start, Text, End
        }

        final Scanner scanner;
        Token token;
        String value;
        String inlineTag;

        private Lexer(Scanner scanner) {
            this.scanner = scanner;
        }

        public void pushText(String rawCommentText) {
            scanner.pushText(rawCommentText);
        }

        boolean next() {
            if (scanner.isEmpty()) {
                token = null;
                value = null;
                return false;
            }

            scanner.mark();
            while (!scanner.isEmpty()) {
                if (inlineTag == null && scanner.lookingAt(HTML_ELEMENT)) {
                    break;
                }
                if (inlineTag == null && scanner.lookingAt(HTML_ENTITY)) {
                    break;
                }
                if (scanner.lookingAt(TAG)) {
                    break;
                }
                if (inlineTag != null && scanner.lookingAt('}')) {
                    break;
                }
                scanner.next();
            }

            String region = scanner.region();
            if (region.length() > 0) {
                token = Token.Text;
                value = region;
                return true;
            }

            if (inlineTag == null && scanner.lookingAt(HTML_ELEMENT)) {
                scanner.next();
                token = Token.Start;
                if (scanner.lookingAt('/')) {
                    token = Token.End;
                    scanner.next();
                }
                scanner.mark();
                scanner.find('>');
                value = scanner.region().toLowerCase();
                scanner.next();
                return true;
            }

            if (inlineTag == null && scanner.lookingAt(HTML_ENTITY)) {
                scanner.next();
                scanner.mark();
                scanner.find(';');
                token = Token.Text;
                value = ENTITIES.get(scanner.region().toLowerCase());
                scanner.next();
                return true;
            }

            if (scanner.lookingAt(TAG)) {
                scanner.next(2);
                scanner.mark();
                scanner.find(END_TAG_NAME);
                token = Token.Start;
                value = scanner.region();
                inlineTag = value;
                scanner.skip(WHITESPACE);
                return true;
            }

            if (inlineTag != null && scanner.lookingAt('}')) {
                token = Token.End;
                value = inlineTag;
                inlineTag = null;
                scanner.next();
                return true;
            }

            throw new IllegalStateException();
        }
    }

    private static class Scanner {
        private final StringBuilder input = new StringBuilder();
        private int pos;
        private int markPos;

        private Scanner(String rawCommentText) {
            pushText(rawCommentText);
        }

        public boolean isEmpty() {
            return pos == input.length();
        }

        public void mark() {
            markPos = pos;
        }

        public void next() {
            next(1);
        }

        public void next(int n) {
            pos += n;
        }

        public boolean lookingAt(char c) {
            return input.charAt(pos) == c;
        }

        public boolean lookingAt(CharSequence prefix) {
            int i = 0;
            int cpos = pos;
            while (i < prefix.length() && cpos < input.length()) {
                if (prefix.charAt(i) != input.charAt(cpos)) {
                    return false;
                }
                i++;
                cpos++;
            }
            return true;
        }

        public boolean lookingAt(Pattern pattern) {
            Matcher m = pattern.matcher(input);
            m.region(pos, input.length());
            return m.lookingAt();
        }

        public String region() {
            return input.substring(markPos, pos);
        }

        /**
         * Moves the position to the next instance of the given character, or the end of the input if not found.
         */
        public void find(char c) {
            int cpos = pos;
            while (cpos < input.length()) {
                if (input.charAt(cpos) == c) {
                    break;
                }
                cpos++;
            }
            pos = cpos;
        }

        /**
         * Moves the position to the start of the next instance of the given pattern, or the end of the input if not
         * found.
         */
        public void find(Pattern pattern) {
            Matcher m = pattern.matcher(input);
            m.region(pos, input.length());
            if (m.find()) {
                pos = m.start();
            } else {
                pos = input.length();
            }
        }

        /**
         * Moves the position over the given pattern if currently looking at the pattern. Does nothing if not.
         */
        public void skip(Pattern pattern) {
            Matcher m = pattern.matcher(input);
            m.region(pos, input.length());
            if (m.lookingAt()) {
                pos = m.end();
            }
        }

        public void pushText(String rawCommentText) {
            if (rawCommentText == null) {
                return;
            }

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
            input.insert(pos, builder.toString().trim());
        }
    }
}
