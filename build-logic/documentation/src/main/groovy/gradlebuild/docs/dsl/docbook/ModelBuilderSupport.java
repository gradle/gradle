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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public class ModelBuilderSupport {
    protected List<Element> children(Element element, String childName) {
        List<Element> matches = new ArrayList<Element>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element childElement = (Element) node;
                if (childElement.getTagName().equals(childName)) {
                    matches.add(childElement);
                }
            }
        }
        return matches;
    }

    protected Element getChild(Element element, String childName) {
        Element child = findChild(element, childName);
        if (child != null) {
            return child;
        }
        throw new RuntimeException(String.format("No <%s> element found in <%s>", childName, element.getTagName()));
    }

    protected Element findChild(Element element, String childName) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element childElement = (Element) node;
                if (childElement.getTagName().equals(childName)) {
                    return childElement;
                }
            }
        }
        return null;
    }
}
