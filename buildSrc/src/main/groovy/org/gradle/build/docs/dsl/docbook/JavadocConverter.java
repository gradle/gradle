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

    public DocComment parse(ClassMetaData classMetaData, GenerationListener listener) {
        listener.start(String.format("class %s", classMetaData));
        try {
            String rawCommentText = classMetaData.getRawCommentText();
            try {
                return parse(rawCommentText, classMetaData, new NoOpCommentSource(), listener);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nComment: %s", classMetaData, rawCommentText), e);
            }
        } finally {
            listener.finish();
        }
    }

    public DocComment parse(final PropertyMetaData propertyMetaData, final GenerationListener listener) {
        listener.start(String.format("property %s", propertyMetaData));
        try {
            ClassMetaData ownerClass = propertyMetaData.getOwnerClass();
            String rawCommentText = propertyMetaData.getRawCommentText();
            try {
                CommentSource commentSource = new InheritedPropertyCommentSource(propertyMetaData, listener);
                DocCommentImpl docComment = parse(rawCommentText, ownerClass, commentSource, listener);
                adjustGetterComment(docComment);
                return docComment;
            } catch (Exception e) {
                throw new GradleException(String.format("Could not convert javadoc comment to docbook.%nClass: %s%nProperty: %s%nComment: %s", ownerClass.getClassName(), propertyMetaData.getName(), rawCommentText), e);
            }
        } finally {
            listener.finish();
        }
    }

    public DocComment parse(final MethodMetaData methodMetaData, final GenerationListener listener) {
        listener.start(String.format("method %s", methodMetaData));
        try {
            ClassMetaData ownerClass = methodMetaData.getOwnerClass();
            String rawCommentText = methodMetaData.getRawCommentText();
            try {
                CommentSource commentSource = new InheritedMethodCommentSource(listener, methodMetaData);
                return parse(rawCommentText, ownerClass, commentSource, listener);
            } catch (Exception e) {
                throw new GradleException(String.format(
                        "Could not convert javadoc comment to docbook.%nClass: %s%nMethod: %s%nComment: %s",
                        ownerClass.getClassName(), methodMetaData.getSignature(), rawCommentText), e);
            }
        } finally {
            listener.finish();
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

    private DocCommentImpl parse(String rawCommentText, ClassMetaData classMetaData,
                                 CommentSource inheritedCommentSource, GenerationListener listener) {
        JavadocLexer lexer = new HtmlToXmlJavadocLexer(new BasicJavadocLexer(new JavadocScanner(rawCommentText)));
        NodeStack nodes = new NodeStack(document);
        final HtmlGeneratingTokenHandler handler = new HtmlGeneratingTokenHandler(nodes, document);
        handler.add(new HtmlElementTranslatingHandler(nodes, document));
        handler.add(new JavadocTagToElementTranslatingHandler(nodes, document));
        handler.add(new HeaderHandler(nodes, document));
        handler.add(new LinkHandler(nodes, linkConverter, classMetaData, listener));
        handler.add(new InheritDocHandler(nodes, inheritedCommentSource));
        handler.add(new ValueHtmlElementHandler(nodes, linkConverter, classMetaData, listener));
        handler.add(new TableHandler(nodes, document));
        handler.add(new AnchorElementHandler(nodes, document, classMetaData));
        handler.add(new AToLinkTranslatingHandler(nodes, document, classMetaData));
        handler.add(new UnknownJavadocTagHandler(nodes, document, listener));
        handler.add(new UnknownHtmlElementHandler(nodes, document, listener));

        lexer.visit(handler);

        nodes.complete();
        return new DocCommentImpl(nodes.nodes);
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
            blockElements.add("orderedlist");
            blockElements.add("listitem");
            blockElements.add("table");
            blockElements.add("tr");
            blockElements.add("td");
            blockElements.add("thead");
        }

        public void appendChild(String text) {
            if (stack.isEmpty() && text.trim().length() == 0) {
                return;
            }
            appendChild(document.createTextNode(text));
        }

        public void appendChild(Node node) {
            boolean blockElement = node instanceof Element && blockElements.contains(node.getNodeName());
            boolean inlineNode = !blockElement && !(node instanceof Element && node.getNodeName().equals("anchor"));
            if (blockElement) {
                endCurrentPara();
            }
            if (stack.isEmpty()) {
                if (!inlineNode) {
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

    private static class HtmlGeneratingTokenHandler extends JavadocLexer.TokenVisitor {
        final NodeStack nodes;
        final List<HtmlElementHandler> elementHandlers = new ArrayList<HtmlElementHandler>();
        final List<JavadocTagHandler> tagHandlers = new ArrayList<JavadocTagHandler>();
        final LinkedList<HtmlElementHandler> handlerStack = new LinkedList<HtmlElementHandler>();
        final LinkedList<String> tagStack = new LinkedList<String>();
        final Map<String, String> attributes = new HashMap<String, String>();
        StringBuilder tagValue;
        final Document document;

        public HtmlGeneratingTokenHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public void add(HtmlElementHandler handler) {
            elementHandlers.add(handler);
        }

        public void add(JavadocTagHandler handler) {
            tagHandlers.add(handler);
        }

        @Override
        void onStartHtmlElement(String name) {
            attributes.clear();
        }

        @Override
        void onHtmlElementAttribute(String name, String value) {
            attributes.put(name, value);
        }

        @Override
        void onStartHtmlElementComplete(String name) {
            for (HtmlElementHandler handler : elementHandlers) {
                if (handler.onStartElement(name, attributes)) {
                    handlerStack.addFirst(handler);
                    tagStack.addFirst(name);
                    return;
                }
            }
            throw new UnsupportedOperationException();
        }

        @Override
        void onEndHtmlElement(String name) {
            if (!tagStack.isEmpty() && tagStack.getFirst().equals(name)) {
                tagStack.removeFirst();
                handlerStack.removeFirst().onEndElement(name);
            }
        }

        @Override
        void onStartJavadocTag(String name) {
            tagValue = new StringBuilder();
        }

        public void onText(String text) {
            if (tagValue != null) {
                tagValue.append(text);
                return;
            }

            if (!handlerStack.isEmpty()) {
                handlerStack.getFirst().onText(text);
                return;
            }

            nodes.appendChild(text);
        }

        @Override
        void onEndJavadocTag(String name) {
            for (JavadocTagHandler handler : tagHandlers) {
                if (handler.onJavadocTag(name, tagValue.toString())) {
                    tagValue = null;
                    return;
                }
            }
            throw new UnsupportedOperationException();
        }
    }

    private interface JavadocTagHandler {
        boolean onJavadocTag(String tag, String value);
    }

    private interface HtmlElementHandler {
        boolean onStartElement(String element, Map<String, String> attributes);

        void onText(String text);

        void onEndElement(String element);
    }

    private static class UnknownJavadocTagHandler implements JavadocTagHandler {
        private final NodeStack nodes;
        private final Document document;
        private final GenerationListener listener;

        private UnknownJavadocTagHandler(NodeStack nodes, Document document, GenerationListener listener) {
            this.nodes = nodes;
            this.document = document;
            this.listener = listener;
        }

        public boolean onJavadocTag(String tag, String value) {
            listener.warning(String.format("Unsupported Javadoc tag '%s'", tag));
            Element element = document.createElement("UNHANDLED-TAG");
            element.appendChild(document.createTextNode(String.format("{@%s %s}", tag, value)));
            nodes.appendChild(element);
            return true;
        }
    }

    private static class UnknownHtmlElementHandler implements HtmlElementHandler {
        private final NodeStack nodes;
        private final Document document;
        private final GenerationListener listener;

        private UnknownHtmlElementHandler(NodeStack nodes, Document document, GenerationListener listener) {
            this.nodes = nodes;
            this.document = document;
            this.listener = listener;
        }

        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            listener.warning(String.format("Unsupported HTML element <%s>", elementName));
            Element element = document.createElement("UNHANDLED-ELEMENT");
            element.appendChild(document.createTextNode(String.format("<%s>", elementName)));
            nodes.push(elementName, element);
            return true;
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }

        public void onEndElement(String elementName) {
            nodes.appendChild(String.format("</%s>", elementName));
            nodes.pop(elementName);
        }
    }

    private static class JavadocTagToElementTranslatingHandler implements JavadocTagHandler {
        private final NodeStack nodes;
        private final Document document;
        private final Map<String, String> tagToElementMap = new HashMap<String, String>();

        private JavadocTagToElementTranslatingHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            tagToElementMap.put("code", "literal");
        }

        public boolean onJavadocTag(String tag, String value) {
            String elementName = tagToElementMap.get(tag);
            if (elementName == null) {
                return false;
            }
            Element element = document.createElement(elementName);
            element.appendChild(document.createTextNode(value));
            nodes.appendChild(element);
            return true;
        }
    }

    private static class HtmlElementTranslatingHandler implements HtmlElementHandler {
        private final NodeStack nodes;
        private final Document document;
        private final Map<String, String> elementToElementMap = new HashMap<String, String>();

        private HtmlElementTranslatingHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            elementToElementMap.put("p", "para");
            elementToElementMap.put("pre", "programlisting");
            elementToElementMap.put("ul", "itemizedlist");
            elementToElementMap.put("ol", "orderedlist");
            elementToElementMap.put("li", "listitem");
            elementToElementMap.put("em", "emphasis");
            elementToElementMap.put("i", "emphasis");
            elementToElementMap.put("b", "emphasis");
            elementToElementMap.put("code", "literal");
        }

        public boolean onStartElement(String element, Map<String, String> attributes) {
            String newElementName = elementToElementMap.get(element);
            if (newElementName == null) {
                return false;
            }
            nodes.push(element, document.createElement(newElementName));
            return true;
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }

        public void onEndElement(String element) {
            nodes.pop(element);
        }
    }

    private static class HeaderHandler implements HtmlElementHandler {
        final NodeStack nodes;
        final Document document;
        int sectionDepth;

        private HeaderHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public boolean onStartElement(String element, Map<String, String> attributes) {
            Matcher matcher = HEADER_PATTERN.matcher(element);
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

        public void onEndElement(String element) {
            nodes.pop("title");
        }
    }

    private static class TableHandler implements HtmlElementHandler {
        private final NodeStack nodes;
        private final Document document;
        private Element currentTable;
        private Element currentRow;
        private Element header;

        public TableHandler(NodeStack nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (elementName.equals("table")) {
                if (currentTable != null) {
                    throw new UnsupportedOperationException("A table within a table is not supported.");
                }
                currentTable = document.createElement("table");
                nodes.push(elementName, currentTable);
                return true;
            }
            if (elementName.equals("tr")) {
                currentRow = document.createElement("tr");
                nodes.push(elementName, currentRow);
                return true;
            }
            if (elementName.equals("th")) {
                if (header == null) {
                    header = document.createElement("thead");
                    currentTable.insertBefore(header, null);
                    header.appendChild(currentRow);
                }
                nodes.push(elementName, document.createElement("td"));
                return true;
            }
            if (elementName.equals("td")) {
                nodes.push(elementName, document.createElement("td"));
                return true;
            }
            return false;
        }

        public void onEndElement(String elementName) {
            if (elementName.equals("table")) {
                currentTable = null;
                header = null;
            }
            if (elementName.equals("tr")) {
                currentRow = null;
            }
            nodes.pop(elementName);
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class AnchorElementHandler implements HtmlElementHandler {
        private final NodeStack nodes;
        private final Document document;
        private final ClassMetaData classMetaData;

        private AnchorElementHandler(NodeStack nodes, Document document, ClassMetaData classMetaData) {
            this.nodes = nodes;
            this.document = document;
            this.classMetaData = classMetaData;
        }

        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (!elementName.equals("a") || !attributes.containsKey("name")) {
                return false;
            }
            Element element = document.createElement("anchor");
            String id = String.format("%s.%s", classMetaData.getClassName(), attributes.get("name"));
            element.setAttribute("id", id);
            nodes.appendChild(element);
            return true;
        }

        public void onEndElement(String element) {
        }

        public void onText(String text) {
        }
    }

    private static class AToLinkTranslatingHandler implements HtmlElementHandler {
        private final NodeStack nodes;
        private final Document document;
        private final ClassMetaData classMetaData;

        private AToLinkTranslatingHandler(NodeStack nodes, Document document, ClassMetaData classMetaData) {
            this.nodes = nodes;
            this.document = document;
            this.classMetaData = classMetaData;
        }

        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (!elementName.equals("a") || !attributes.containsKey("href")) {
                return false;
            }
            String href = attributes.get("href");
            if (!href.startsWith("#")) {
                return false;
            }
            Element element = document.createElement("link");
            String targetId = String.format("%s.%s", classMetaData.getClassName(), href.substring(1));
            element.setAttribute("linkend", targetId);
            nodes.push(elementName, element);
            return true;
        }

        public void onEndElement(String element) {
            nodes.pop(element);
        }

        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class ValueHtmlElementHandler implements JavadocTagHandler {
        private final JavadocLinkConverter linkConverter;
        private final ClassMetaData classMetaData;
        private final NodeStack nodes;
        private final GenerationListener listener;

        public ValueHtmlElementHandler(NodeStack nodes, JavadocLinkConverter linkConverter, ClassMetaData classMetaData,
                                       GenerationListener listener) {
            this.nodes = nodes;
            this.linkConverter = linkConverter;
            this.classMetaData = classMetaData;
            this.listener = listener;
        }

        public boolean onJavadocTag(String tag, String value) {
            if (!tag.equals("value")) {
                return false;
            }
            nodes.appendChild(linkConverter.resolveValue(value, classMetaData, listener));
            return true;
        }
    }

    private static class LinkHandler implements JavadocTagHandler {
        private final NodeStack nodes;
        private final JavadocLinkConverter linkConverter;
        private final ClassMetaData classMetaData;
        private final GenerationListener listener;

        private LinkHandler(NodeStack nodes, JavadocLinkConverter linkConverter, ClassMetaData classMetaData,
                            GenerationListener listener) {
            this.nodes = nodes;
            this.linkConverter = linkConverter;
            this.classMetaData = classMetaData;
            this.listener = listener;
        }

        public boolean onJavadocTag(String tag, String value) {
            if (!tag.equals("link")) {
                return false;
            }
            nodes.appendChild(linkConverter.resolve(value, classMetaData, listener));
            return true;
        }
    }

    private static class InheritDocHandler implements JavadocTagHandler {
        private final CommentSource source;
        private final NodeStack nodeStack;

        private InheritDocHandler(NodeStack nodeStack, CommentSource source) {
            this.nodeStack = nodeStack;
            this.source = source;
        }

        public boolean onJavadocTag(String tag, String value) {
            if (!tag.equals("inheritDoc")) {
                return false;
            }
            for (Node node : source.getCommentText()) {
                nodeStack.appendChild(node);
            }
            return true;
        }
    }

    private interface CommentSource {
        Iterable<? extends Node> getCommentText();
    }

    private static class NoOpCommentSource implements CommentSource {
        public List<? extends Node> getCommentText() {
            throw new UnsupportedOperationException();
        }
    }

    private class InheritedPropertyCommentSource implements CommentSource {
        private final PropertyMetaData propertyMetaData;
        private final GenerationListener listener;

        public InheritedPropertyCommentSource(PropertyMetaData propertyMetaData, GenerationListener listener) {
            this.propertyMetaData = propertyMetaData;
            this.listener = listener;
        }

        public Iterable<? extends Node> getCommentText() {
            PropertyMetaData overriddenProperty = propertyMetaData.getOverriddenProperty();
            if (overriddenProperty == null) {
                listener.warning("No inherited javadoc comment found.");
                return Arrays.asList(document.createTextNode("!!NO INHERITED DOC COMMENT!!"));
            }
            return parse(overriddenProperty, listener).getDocbook();
        }
    }

    private class InheritedMethodCommentSource implements CommentSource {
        private final GenerationListener listener;
        private final MethodMetaData methodMetaData;

        public InheritedMethodCommentSource(GenerationListener listener, MethodMetaData methodMetaData) {
            this.listener = listener;
            this.methodMetaData = methodMetaData;
        }

        public Iterable<? extends Node> getCommentText() {
            MethodMetaData overriddenMethod = methodMetaData.getOverriddenMethod();
            if (overriddenMethod == null) {
                listener.warning("No inherited javadoc comment found.");
                return Arrays.asList(document.createTextNode("!!NO INHERITED DOC COMMENT!!"));
            }

            return parse(overriddenMethod, listener).getDocbook();
        }
    }
}
