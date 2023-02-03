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

import gradlebuild.docs.dsl.docbook.model.PropertyDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class PropertyTableRenderer {
    public void renderTo(Iterable<PropertyDoc> properties, Element parent) {
        Document document = parent.getOwnerDocument();

        // <thead>
        //   <tr>
        //     <td>Property</td>
        //     <td>Description</td>
        //   </tr>
        // </thead>
        Element thead = document.createElement("thead");
        parent.appendChild(thead);
        Element tr = document.createElement("tr");
        thead.appendChild(tr);
        Element td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Property"));
        td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Description"));

        for (PropertyDoc propDoc : properties) {
            // <tr>
            //   <td><link linkend="$id"><literal>$name</literal></link</td>
            //   <td>$description</td>
            // </tr>
            tr = document.createElement("tr");
            parent.appendChild(tr);

            td = document.createElement("td");
            tr.appendChild(td);
            Element link = document.createElement("link");
            td.appendChild(link);
            link.setAttribute("linkend", propDoc.getId());
            Element literal = document.createElement("literal");
            link.appendChild(literal);
            literal.appendChild(document.createTextNode(propDoc.getName()));

            td = document.createElement("td");
            tr.appendChild(td);
            if (propDoc.isDeprecated()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Deprecated"));
            }
            if (propDoc.isIncubating()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Incubating"));
            }
            if (propDoc.isReplaced()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Replaced"));
            }
            td.appendChild(document.importNode(propDoc.getDescription(), true));
        }
    }
}
