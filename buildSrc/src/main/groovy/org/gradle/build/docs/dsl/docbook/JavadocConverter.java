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

import org.gradle.api.GradleException;
import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.dsl.model.MethodMetaData;
import org.gradle.build.docs.dsl.model.PropertyMetaData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

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

    public DocComment parse(ClassMetaData classMetaData) {
        CommentSource commentSource = new CommentSource() {
            public List<? extends Node> getCommentText() {
                throw new UnsupportedOperationException();
            }
        };

        String rawCommentText = classMetaData.getRawCommentText();
        try {
            return parse(rawCommentText, classMetaData, commentSource);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nComment: %s", classMetaData.getClassName(), rawCommentText), e);
        }
    }

    public DocComment parse(final PropertyMetaData propertyMetaData) {
        CommentSource commentSource = new CommentSource() {
            public Iterable<? extends Node> getCommentText() {
                PropertyMetaData overriddenProperty = propertyMetaData.getOverriddenProperty();
                if (overriddenProperty == null) {
                    return Arrays.asList(document.createTextNode("!!NO INHERITED DOC COMMENT!!"));
                }
                return parse(overriddenProperty).getDocbook();
            }
        };

        ClassMetaData ownerClass = propertyMetaData.getOwnerClass();
        String rawCommentText = propertyMetaData.getRawCommentText();
        try {
            DocCommentImpl docComment = parse(rawCommentText, ownerClass, commentSource);
            adjustGetterComment(docComment);
            return docComment;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nProperty: %s%nComment: %s", ownerClass.getClassName(), propertyMetaData.getName(), rawCommentText), e);
        }
    }

    public DocComment parse(final MethodMetaData methodMetaData) {
        CommentSource commentSource = new CommentSource() {
            public Iterable<? extends Node> getCommentText() {
                return Arrays.asList(document.createTextNode("!!NO INHERITED DOC COMMENT!!"));
            }
        };

        ClassMetaData ownerClass = methodMetaData.getOwnerClass();
        String rawCommentText = methodMetaData.getRawCommentText();
        try {
            DocCommentImpl docComment = parse(rawCommentText, ownerClass, commentSource);
            adjustGetterComment(docComment);
            return docComment;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%Method: %s%nComment: %s", ownerClass.getClassName(), methodMetaData.getSignature(), rawCommentText), e);
        }
    }

    private void adjustGetterComment(DocCommentImpl docComment) {
        // Replace 'Returns the ...' with 'The ...'
        List<Element> nodes = docComment.getDocbook();
        if (nodes.isEmpty()) {
            return;
        }

        Element firstNode = nodes.get(0);
        if (!firstNode.getNodeName().equals("para") || !(firstNode.getFirstChild() instanceof Text)) {
            return;
        }

        Text comment = (Text) firstNode.getFirstChild();
        Pattern getterPattern = Pattern.compile("returns\\s+the\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = getterPattern.matcher(comment.getData());
        if (matcher.lookingAt()) {
            comment.setData("The " + comment.getData().substring(matcher.end()));
        }
    }

    private DocCommentImpl parse(String rawCommentText, ClassMetaData classMetaData, CommentSource inheritedCommentSource) {
        List<Element> nodes = textToDom(rawCommentText, classMetaData, inheritedCommentSource);
        return new DocCommentImpl(nodes);
    }

    private List<Element> textToDom(String rawCommentText, ClassMetaData classMetaData, CommentSource inheritedCommentSource) {
        JavadocLexer lexer = new JavadocLexer(new JavadocScanner(rawCommentText));
        NodeStack nodes = new NodeStack(document);
        HtmlGeneratingTokenHandler handler = new HtmlGeneratingTokenHandler(nodes, document);
        handler.add(new TagToElementTranslatingTokenHandler(nodes, document));
        handler.add(new HeaderHandler(nodes, document));
        handler.add(new LinkHandler(nodes, linkConverter, classMetaData));
        handler.add(new InheritDocHandler(nodes, inheritedCommentSource));
        handler.add(new UnknownElementTagHandler(nodes, document));

        while (lexer.next()) {
            switch (lexer.token) {
                case Text:
                    handler.onText(lexer.value);
                    break;
                case StartElement:
                case StartTag:
                    handler.onStartTag(lexer.value, lexer.token);
                    break;
                case End:
                    handler.onEndTag(lexer.value);
                    break;
            }
        }

        nodes.complete();
        return nodes.nodes;
    }

    private static class DocCommentImpl implements DocComment {
        private final List<Element> nodes;

        public DocCommentImpl(List<Element> nodes) {
            this.nodes = nodes;
        }

        public List<Element> getDocbook() {
            return nodes;
        }
    }

    private static class NodeStack {
        final Set<String> blockElements = new HashSet<String>();
        final List<Element> nodes = new ArrayList<Element>();
        final LinkedList<Element> stack = new LinkedList<Element>();
        final LinkedList<String> tags = new LinkedList<String>();
        final Document document;

        private NodeStack(Document document) {
            this.document = document;
            blockElements.add("para");
            blockElements.add("section");
            blockElements.add("title");
            blockElements.add("programlisting");
            blockElements.add("itemizedlist");
            blockElements.add("listitem");
        }

        public void appendChild(String text) {
            if (stack.isEmpty() && text.trim().length() == 0) {
                return;
            }
            appendChild(document.createTextNode(text));
        }

        public void appendChild(Node node) {
            boolean blockElement = node instanceof Element && blockElements.contains(node.getNodeName());
            if (blockElement) {
                endCurrentPara();
            }
            if (stack.isEmpty()) {
                if (blockElement) {
                    appendToResult((Element) node);
                } else {
                    Element wrapper = document.createElement("para");
                    wrapper.appendChild(node);
                    stack.addFirst(wrapper);
                    tags.addFirst("");
                }
            } else {
                stack.getFirst().appendChild(node);
            }
        }

        public void push(String tag, Element element) {
            boolean blockElement = blockElements.contains(element.getNodeName());
            if (blockElement) {
                endCurrentPara();
            }
            if (stack.isEmpty()) {
                if (blockElement) {
                    stack.addFirst(element);
                    tags.addFirst(tag);
                } else {
                    Element wrapper = document.createElement("para");
                    wrapper.appendChild(element);
                    stack.addFirst(wrapper);
                    tags.addFirst("");
                    stack.addFirst(element);
                    tags.addFirst(tag);
                }
            } else {
                stack.getFirst().appendChild(element);
                stack.addFirst(element);
                tags.addFirst(tag);
            }
        }

        public Element pop(String tag) {
            Element element = null;
            if (!tags.isEmpty() && tags.getFirst().equals(tag)) {
                element = stack.removeFirst();
                tags.removeFirst();
                if (stack.isEmpty()) {
                    appendToResult(element);
                }
            }
            return element;
        }

        private void endCurrentPara() {
            if (stack.isEmpty() || !stack.getFirst().getNodeName().equals("para")) {
                return;
            }

            Element para = stack.removeFirst();
            tags.removeFirst();
            if (stack.isEmpty()) {
                appendToResult(para);
            }
        }

        private void appendToResult(Element element) {
            if (element.getFirstChild() == null && element.getAttributes().getLength() == 0) {
                return;
            }
            nodes.add(element);
        }

        public void complete() {
            if (!stack.isEmpty()) {
                appendToResult(stack.getLast());
            }
            stack.clear();
            tags.clear();
        }
    }

    private static class HtmlGeneratingTokenHandler {
        final NodeStack nodes;
        final List<TagHandler> handlers = new ArrayList<TagHandler>();
        final LinkedList<TagHandler> handlerStack = new LinkedList<TagHandler>();
        final LinkedList<String> tagStack = new LinkedList<String>();
        final Document document;

        public HtmlGeneratingTokenHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public void add(TagHandler handler) {
            handlers.add(handler);
        }

        public void onStartTag(String name, JavadocLexer.Token token) {
            for (TagHandler handler : handlers) {
                if (handler.onStartTag(name, token)) {
                    handlerStack.addFirst(handler);
                    tagStack.addFirst(name);
                    return;
                }
            }
            throw new UnsupportedOperationException();
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
        boolean onStartTag(String tag, JavadocLexer.Token token);

        void onText(String text);

        void onEndTag(String tag);
    }

    private static class UnknownElementTagHandler implements TagHandler {
        private final NodeStack nodes;
        private final Document document;

        private UnknownElementTagHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public boolean onStartTag(String tag, JavadocLexer.Token token) {
            Element element = document.createElement(token == JavadocLexer.Token.StartElement ? "UNHANDLED-ELEMENT" : "UNHANDLED-TAG");
            element.appendChild(document.createTextNode(String.format("<%s>", tag)));
            nodes.push(tag, element);
            return true;
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }

        public void onEndTag(String tag) {
            nodes.pop(tag);
        }
    }

    private static class TagToElementTranslatingTokenHandler implements TagHandler {
        private final NodeStack nodes;
        private final Document document;
        private final Map<String, String> tagToElementMap = new HashMap<String, String>();
        private final Map<String, String> elementToElementMap = new HashMap<String, String>();

        private TagToElementTranslatingTokenHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            elementToElementMap.put("p", "para");
            elementToElementMap.put("pre", "programlisting");
            elementToElementMap.put("ul", "itemizedlist");
            elementToElementMap.put("li", "listitem");
            elementToElementMap.put("em", "emphasis");
            elementToElementMap.put("i", "emphasis");
            elementToElementMap.put("b", "emphasis");
            elementToElementMap.put("code", "literal");

            tagToElementMap.put("code", "literal");
        }

        public boolean onStartTag(String tag, JavadocLexer.Token token) {
            String element;
            if (token == JavadocLexer.Token.StartTag) {
                element = tagToElementMap.get(tag);
            } else {
                element = elementToElementMap.get(tag);
            }
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

        public boolean onStartTag(String tag, JavadocLexer.Token token) {
            if (token != JavadocLexer.Token.StartElement) {
                return false;
            }
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

        public boolean onStartTag(String tag, JavadocLexer.Token token) {
            if (token != JavadocLexer.Token.StartTag || !tag.equals("link")) {
                return false;
            }
            link = new StringBuilder();
            return true;
        }

        public void onText(String text) {
            link.append(text);
        }

        public void onEndTag(String tag) {
            String className = link.toString().split("\\s+")[0];
            for (Node node : linkConverter.resolve(className, classMetaData)) {
                nodes.appendChild(node);
            }
            link = null;
        }
    }

    private static class InheritDocHandler implements TagHandler {
        private final CommentSource source;
        private final NodeStack nodeStack;

        private InheritDocHandler(NodeStack nodeStack, CommentSource source) {
            this.nodeStack = nodeStack;
            this.source = source;
        }

        public boolean onStartTag(String tag, JavadocLexer.Token token) {
            return token == JavadocLexer.Token.StartTag && tag.equals("inheritDoc");
        }

        public void onText(String text) {
            // ignore
        }

        public void onEndTag(String tag) {
            for (Node node : source.getCommentText()) {
                nodeStack.appendChild(node);
            }
        }
    }

    private interface CommentSource {
        Iterable<? extends Node> getCommentText();
    }

}
