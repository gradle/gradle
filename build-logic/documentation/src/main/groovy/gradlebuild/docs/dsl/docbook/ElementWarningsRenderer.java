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

import gradlebuild.docs.dsl.docbook.model.DslElementDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ElementWarningsRenderer {
    public void renderTo(DslElementDoc elementDoc, String type, Element parent) {
        if (elementDoc.isDeprecated()) {
            Document document = parent.getOwnerDocument();
            Element caution = document.createElement("caution");
            parent.appendChild(caution);
            Element para = document.createElement("para");
            caution.appendChild(para);
            para.appendChild(document.createTextNode(String.format("Note: This %s is ", type)));
            Element link = document.createElement("ulink");
            para.appendChild(link);
            link.setAttribute("url", "../userguide/feature_lifecycle.html");
            link.appendChild(document.createTextNode("deprecated"));
            para.appendChild(document.createTextNode(" and will be removed in the next major version of Gradle."));
        }
        if (elementDoc.isIncubating()) {
            Document document = parent.getOwnerDocument();
            Element caution = document.createElement("caution");
            parent.appendChild(caution);
            Element para = document.createElement("para");
            caution.appendChild(para);
            para.appendChild(document.createTextNode(String.format("Note: This %s is ", type)));
            Element link = document.createElement("ulink");
            para.appendChild(link);
            link.setAttribute("url", "../userguide/feature_lifecycle.html");
            link.appendChild(document.createTextNode("incubating"));
            para.appendChild(document.createTextNode(" and may change in a future version of Gradle."));
        }
        if (elementDoc.isReplaced()) {
            Document document = parent.getOwnerDocument();
            Element caution = document.createElement("caution");
            parent.appendChild(caution);
            Element para = document.createElement("para");
            caution.appendChild(para);
            para.appendChild(document.createTextNode(String.format("Note: This %s has been replaced by %s.", type, elementDoc.getReplacement())));
        }
    }
}
