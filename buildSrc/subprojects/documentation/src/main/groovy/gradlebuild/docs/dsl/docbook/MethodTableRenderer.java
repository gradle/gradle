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

import gradlebuild.docs.dsl.docbook.model.MethodDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MethodTableRenderer {
    public void renderTo(Iterable<MethodDoc> methods, Element parent) {
        Document document = parent.getOwnerDocument();

        // <thead>
        //   <tr>
        //     <td>Method</td>
        //     <td>Description</td>
        //   </tr>
        // </thead>
        Element thead = document.createElement("thead");
        parent.appendChild(thead);
        Element tr = document.createElement("tr");
        thead.appendChild(tr);
        Element td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Method"));
        td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Description"));

        for (MethodDoc methodDoc : methods) {
            // <tr>
            //   <td><literal><link linkend="$id">$name</link>$signature</literal></td>
            //   <td>$description</td>
            // </tr>
            tr = document.createElement("tr");
            parent.appendChild(tr);

            td = document.createElement("td");
            tr.appendChild(td);
            Element literal = document.createElement("literal");
            td.appendChild(literal);
            Element link = document.createElement("link");
            literal.appendChild(link);
            link.setAttribute("linkend", methodDoc.getId());
            link.appendChild(document.createTextNode(methodDoc.getName()));
            StringBuilder signature = new StringBuilder();
            signature.append("(");
            for (int i = 0; i < methodDoc.getMetaData().getParameters().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(methodDoc.getMetaData().getParameters().get(i).getName());
            }
            signature.append(")");
            literal.appendChild(document.createTextNode(signature.toString()));

            td = document.createElement("td");
            tr.appendChild(td);
            if (methodDoc.isDeprecated()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Deprecated"));
            }
            if (methodDoc.isIncubating()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Incubating"));
            }
            td.appendChild(document.importNode(methodDoc.getDescription(), true));
        }
    }
}
