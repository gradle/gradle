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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.build.docs.dsl.source.model.ClassMetaData;
import org.gradle.build.docs.dsl.source.model.MethodMetaData;
import org.gradle.build.docs.dsl.source.model.PropertyMetaData;
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
    private static final Pattern ACCESSOR_COMMENT_PATTERN = Pattern.compile("(?:returns|sets)\\s+(the|whether)\\s+", Pattern.CASE_INSENSITIVE);
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
                adjustAccessorComment(docComment);
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

    private void adjustAccessorComment(DocCommentImpl docComment) {
        // Replace 'Returns the ...'/'Sets the ...' with 'The ...'
        List<Element> nodes = docComment.getDocbook();
        if (nodes.isEmpty()) {
            return;
        }

        Element firstNode = nodes.get(0);
        if (!firstNode.getNodeName().equals("para") || !(firstNode.getFirstChild() instanceof Text)) {
            return;
        }

        Text comment = (Text) firstNode.getFirstChild();
        Matcher matcher = ACCESSOR_COMMENT_PATTERN.matcher(comment.getData());
        if (matcher.lookingAt()) {
            String theOrWhether = matcher.group(1).toLowerCase(Locale.US);
            comment.setData(StringUtils.capitalize(theOrWhether) + " " + comment.getData().substring(matcher.end()));
        }
    }

    private DocCommentImpl parse(String rawCommentText, ClassMetaData classMetaData,
                                 CommentSource inheritedCommentSource, GenerationListener listener) {
        JavadocLexer lexer = new HtmlToXmlJavadocLexer(new BasicJavadocLexer(new JavadocScanner(rawCommentText)));
        DocBookBuilder nodes = new DocBookBuilder(document);
        final HtmlGeneratingTokenHandler handler = new HtmlGeneratingTokenHandler(nodes, document);
        handler.add(new HtmlElementTranslatingHandler(nodes, document));
        handler.add(new PreElementHandler(nodes, document));
        handler.add(new JavadocTagToElementTranslatingHandler(nodes, document));
        handler.add(new HeaderHandler(nodes, document));
        handler.add(new LinkHandler(nodes, linkConverter, classMetaData, listener));
        handler.add(new InheritDocHandler(nodes, inheritedCommentSource));
        handler.add(new ValueTagHandler(nodes, linkConverter, classMetaData, listener));
        handler.add(new LiteralTagHandler(nodes));
        handler.add(new TableHandler(nodes, document));
        handler.add(new DlElementHandler(nodes, document));
        handler.add(new AnchorElementHandler(nodes, document, classMetaData));
        handler.add(new AToLinkTranslatingHandler(nodes, document, classMetaData));
        handler.add(new AToUlinkTranslatingHandler(nodes, document));
        handler.add(new UnknownJavadocTagHandler(nodes, document, listener));
        handler.add(new UnknownHtmlElementHandler(nodes, document, listener));

        lexer.visit(handler);

        return new DocCommentImpl(nodes.getElements());
    }

    private static class DocCommentImpl implements DocComment {
        private final List<Element> nodes;

        public DocCommentImpl(List<Element> nodes) {
            this.nodes = nodes;
        }

        @Override
        public List<Element> getDocbook() {
            return nodes;
        }
    }

    private static class HtmlGeneratingTokenHandler extends JavadocLexer.TokenVisitor {
        final DocBookBuilder nodes;
        final List<HtmlElementHandler> elementHandlers = new ArrayList<HtmlElementHandler>();
        final List<JavadocTagHandler> tagHandlers = new ArrayList<JavadocTagHandler>();
        final LinkedList<HtmlElementHandler> handlerStack = new LinkedList<HtmlElementHandler>();
        final LinkedList<String> tagStack = new LinkedList<String>();
        final Map<String, String> attributes = new HashMap<String, String>();
        StringBuilder tagValue;
        final Document document;

        public HtmlGeneratingTokenHandler(DocBookBuilder nodes, Document document) {
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

        @Override
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
        private final DocBookBuilder nodes;
        private final Document document;
        private final GenerationListener listener;

        private UnknownJavadocTagHandler(DocBookBuilder nodes, Document document, GenerationListener listener) {
            this.nodes = nodes;
            this.document = document;
            this.listener = listener;
        }

        @Override
        public boolean onJavadocTag(String tag, String value) {
            listener.warning(String.format("Unsupported Javadoc tag '%s'", tag));
            Element element = document.createElement("UNHANDLED-TAG");
            element.appendChild(document.createTextNode(String.format("{@%s %s}", tag, value)));
            nodes.appendChild(element);
            return true;
        }
    }

    private static class UnknownHtmlElementHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private final GenerationListener listener;

        private UnknownHtmlElementHandler(DocBookBuilder nodes, Document document, GenerationListener listener) {
            this.nodes = nodes;
            this.document = document;
            this.listener = listener;
        }

        @Override
        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            listener.warning(String.format("Unsupported HTML element <%s>", elementName));
            Element element = document.createElement("UNHANDLED-ELEMENT");
            element.appendChild(document.createTextNode(String.format("<%s>", elementName)));
            nodes.push(element);
            return true;
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }

        @Override
        public void onEndElement(String elementName) {
            nodes.appendChild(String.format("</%s>", elementName));
            nodes.pop();
        }
    }

    private static class JavadocTagToElementTranslatingHandler implements JavadocTagHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private final Map<String, String> tagToElementMap = new HashMap<String, String>();

        private JavadocTagToElementTranslatingHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            tagToElementMap.put("code", "literal");
        }

        @Override
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
        private final DocBookBuilder nodes;
        private final Document document;
        private final Map<String, String> elementToElementMap = new HashMap<String, String>();

        private HtmlElementTranslatingHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
            elementToElementMap.put("p", "para");
            elementToElementMap.put("ul", "itemizedlist");
            elementToElementMap.put("ol", "orderedlist");
            elementToElementMap.put("li", "listitem");
            elementToElementMap.put("em", "emphasis");
            elementToElementMap.put("strong", "emphasis");
            elementToElementMap.put("i", "emphasis");
            elementToElementMap.put("b", "emphasis");
            elementToElementMap.put("code", "literal");
            elementToElementMap.put("tt", "literal");
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            String newElementName = elementToElementMap.get(element);
            if (newElementName == null) {
                return false;
            }
            nodes.push(document.createElement(newElementName));
            return true;
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }

        @Override
        public void onEndElement(String element) {
            nodes.pop();
        }
    }

    private static class PreElementHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;

        private PreElementHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        @Override
        public boolean onStartElement(String element, Map<String, String> attributes) {
            if (!"pre".equals(element)) {
                return false;
            }
            Element newElement = document.createElement("programlisting");
            //we're making an assumption that all <pre> elements contain java code
            //this should mostly be true :)
            //if it isn't true then the syntax highlighting won't spoil the view too much anyway
            newElement.setAttribute("language", "java");
            nodes.push(newElement);
            return true;
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }

        @Override
        public void onEndElement(String element) {
            nodes.pop();
        }
    }

    private static class HeaderHandler implements HtmlElementHandler {
        final DocBookBuilder nodes;
        final Document document;
        int sectionDepth;

        private HeaderHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        @Override
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
                nodes.pop();
                sectionDepth--;
            }
            Element section = document.createElement("section");
            while (sectionDepth < depth) {
                nodes.push(section);
                sectionDepth++;
            }
            nodes.push(document.createElement("title"));
            sectionDepth = depth;
            return true;
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }

        @Override
        public void onEndElement(String element) {
            nodes.pop();
        }
    }

    private static class TableHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private Element currentTable;
        private Element currentRow;
        private Element header;

        public TableHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        @Override
        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (elementName.equals("table")) {
                if (currentTable != null) {
                    throw new UnsupportedOperationException("A table within a table is not supported.");
                }
                currentTable = document.createElement("table");
                nodes.push(currentTable);
                return true;
            }
            if (elementName.equals("tr")) {
                currentRow = document.createElement("tr");
                nodes.push(currentRow);
                return true;
            }
            if (elementName.equals("th")) {
                if (header == null) {
                    header = document.createElement("thead");
                    currentTable.insertBefore(header, null);
                    header.appendChild(currentRow);
                }
                nodes.push(document.createElement("td"));
                return true;
            }
            if (elementName.equals("td")) {
                nodes.push(document.createElement("td"));
                return true;
            }
            return false;
        }

        @Override
        public void onEndElement(String elementName) {
            if (elementName.equals("table")) {
                currentTable = null;
                header = null;
            }
            if (elementName.equals("tr")) {
                currentRow = null;
            }
            nodes.pop();
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class AnchorElementHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private final ClassMetaData classMetaData;

        private AnchorElementHandler(DocBookBuilder nodes, Document document, ClassMetaData classMetaData) {
            this.nodes = nodes;
            this.document = document;
            this.classMetaData = classMetaData;
        }

        @Override
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

        @Override
        public void onEndElement(String element) {
        }

        @Override
        public void onText(String text) {
        }
    }

    private static class AToLinkTranslatingHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private final ClassMetaData classMetaData;

        private AToLinkTranslatingHandler(DocBookBuilder nodes, Document document, ClassMetaData classMetaData) {
            this.nodes = nodes;
            this.document = document;
            this.classMetaData = classMetaData;
        }

        @Override
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
            nodes.push(element);
            return true;
        }

        @Override
        public void onEndElement(String element) {
            nodes.pop();
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class AToUlinkTranslatingHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;

        private AToUlinkTranslatingHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        @Override
        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (!elementName.equals("a") || !attributes.containsKey("href")) {
                return false;
            }
            String href = attributes.get("href");
            if (href.startsWith("#")) {
                return false;
            }
            Element element = document.createElement("ulink");
            element.setAttribute("url", href);
            nodes.push(element);
            return true;
        }

        @Override
        public void onEndElement(String element) {
            nodes.pop();
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class DlElementHandler implements HtmlElementHandler {
        private final DocBookBuilder nodes;
        private final Document document;
        private Element currentList;
        private Element currentItem;

        public DlElementHandler(DocBookBuilder nodes, Document document) {
            this.nodes = nodes;
            this.document = document;
        }

        @Override
        public boolean onStartElement(String elementName, Map<String, String> attributes) {
            if (elementName.equals("dl")) {
                if (currentList != null) {
                    throw new UnsupportedOperationException("<dl> within a <dl> is not supported.");
                }
                currentList = document.createElement("variablelist");
                nodes.push(currentList);
                return true;
            }
            if (elementName.equals("dt")) {
                if (currentItem != null) {
                    nodes.pop();
                }
                currentItem = document.createElement("varlistentry");
                nodes.push(currentItem);
                nodes.push(document.createElement("term"));
                return true;
            }
            if (elementName.equals("dd")) {
                if (currentItem == null) {
                    throw new IllegalStateException("No <dt> element preceding <dd> element.");
                }
                nodes.push(document.createElement("listitem"));
                return true;
            }

            return false;
        }

        @Override
        public void onEndElement(String element) {
            if (element.equals("dl")) {
                currentList = null;
                if (currentItem != null) {
                    currentItem = null;
                    nodes.pop();
                }
                nodes.pop();
            }
            if (element.equals("dt")) {
                nodes.pop();
            }
            if (element.equals("dd")) {
                nodes.pop();
            }
        }

        @Override
        public void onText(String text) {
            nodes.appendChild(text);
        }
    }

    private static class ValueTagHandler implements JavadocTagHandler {
        private final JavadocLinkConverter linkConverter;
        private final ClassMetaData classMetaData;
        private final DocBookBuilder nodes;
        private final GenerationListener listener;

        public ValueTagHandler(DocBookBuilder nodes, JavadocLinkConverter linkConverter, ClassMetaData classMetaData,
                               GenerationListener listener) {
            this.nodes = nodes;
            this.linkConverter = linkConverter;
            this.classMetaData = classMetaData;
            this.listener = listener;
        }

        @Override
        public boolean onJavadocTag(String tag, String value) {
            if (!tag.equals("value")) {
                return false;
            }
            nodes.appendChild(linkConverter.resolveValue(value, classMetaData, listener));
            return true;
        }
    }

    private static class LiteralTagHandler implements JavadocTagHandler {
        private final DocBookBuilder nodes;

        private LiteralTagHandler(DocBookBuilder nodes) {
            this.nodes = nodes;
        }

        @Override
        public boolean onJavadocTag(String tag, String value) {
            if (!tag.equals("literal")) {
                return false;
            }
            nodes.appendChild(value);
            return true;
        }
    }

    private static class LinkHandler implements JavadocTagHandler {
        private final DocBookBuilder nodes;
        private final JavadocLinkConverter linkConverter;
        private final ClassMetaData classMetaData;
        private final GenerationListener listener;

        private LinkHandler(DocBookBuilder nodes, JavadocLinkConverter linkConverter, ClassMetaData classMetaData,
                            GenerationListener listener) {
            this.nodes = nodes;
            this.linkConverter = linkConverter;
            this.classMetaData = classMetaData;
            this.listener = listener;
        }

        @Override
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
        private final DocBookBuilder nodeStack;

        private InheritDocHandler(DocBookBuilder nodeStack, CommentSource source) {
            this.nodeStack = nodeStack;
            this.source = source;
        }

        @Override
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
        @Override
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

        @Override
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

        @Override
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
