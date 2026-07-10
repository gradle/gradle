/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.docbook;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class DocBookBuilder {
    final LinkedList<Element> stack = new LinkedList<Element>();
    final Document document;

    DocBookBuilder(Document document) {
        this.document = document;
        stack.addFirst(document.createElement("root"));
    }

    List<Element> getElements() {
        List<Element> elements = new ArrayList<Element>();
        for (Node node = stack.getLast().getFirstChild(); node != null; node = node.getNextSibling()) {
            elements.add((Element) node);
        }
        return elements;
    }

    public void appendChild(String text) {
        appendChild(document.createTextNode(text));
    }

    public void appendChild(Node node) {
        boolean inPara = false;
        if (node instanceof Element) {
            Element element = (Element) node;
            if (element.getTagName().equals("para") && stack.getFirst().getTagName().equals("para")) {
                pop();
                inPara = true;
            }
        }
        stack.getFirst().appendChild(node);
        if (inPara) {
            Element para = document.createElement("para");
            push(para);
        }
    }

    public void push(Element element) {
        stack.getFirst().appendChild(element);
        stack.addFirst(element);
    }

    public Element pop() {
        Element element = stack.removeFirst();
        if (emptyPara(element)) {
            element.getParentNode().removeChild(element);
        }
        return element;
    }

    private boolean emptyPara(Element element) {
        if (!element.getTagName().equals("para")) {
            return false;
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Text)) {
                return false;
            }
            Text text = (Text) node;
            if (!text.getTextContent().matches("\\s*")) {
                return false;
            }
        }
        return true;
    }
}
