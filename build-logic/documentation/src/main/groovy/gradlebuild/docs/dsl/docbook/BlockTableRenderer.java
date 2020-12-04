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

import gradlebuild.docs.dsl.docbook.model.BlockDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BlockTableRenderer {
    public void renderTo(Iterable<BlockDoc> blocks, Element parent) {
        Document document = parent.getOwnerDocument();

        // <thead>
        //   <tr>
        //     <td>Block</td>
        //     <td>Description</td>
        //   </tr>
        // </thead>
        Element thead = document.createElement("thead");
        parent.appendChild(thead);
        Element tr = document.createElement("tr");
        thead.appendChild(tr);
        Element td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Block"));
        td = document.createElement("td");
        tr.appendChild(td);
        td.appendChild(document.createTextNode("Description"));

        for (BlockDoc blockDoc : blocks) {
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
            link.setAttribute("linkend", blockDoc.getId());
            Element literal = document.createElement("literal");
            link.appendChild(literal);
            literal.appendChild(document.createTextNode(blockDoc.getName()));

            td = document.createElement("td");
            tr.appendChild(td);
            if (blockDoc.isDeprecated()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Deprecated"));
            }
            if (blockDoc.isIncubating()) {
                Element caution = document.createElement("caution");
                td.appendChild(caution);
                caution.appendChild(document.createTextNode("Incubating"));
            }
            td.appendChild(document.importNode(blockDoc.getDescription(), true));
        }
    }
}
