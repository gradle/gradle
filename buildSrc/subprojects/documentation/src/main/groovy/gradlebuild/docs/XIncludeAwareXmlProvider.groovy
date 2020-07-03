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
package gradlebuild.docs

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class XIncludeAwareXmlProvider {

    Document root

    Element parse(File sourceFile) {
        root = parseSourceFile(sourceFile)
        root.documentElement
    }

    Node emptyDoc() {
        root = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    }

    void write(File destFile, boolean indent = false) {
        destFile.withOutputStream { OutputStream stream ->
            TransformerFactory factory = TransformerFactory.newInstance()
            Transformer transformer = factory.newTransformer()
            if (indent) {
                transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            }
            transformer.setOutputProperty(OutputKeys.METHOD, "xml")
            transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/xml")
            transformer.transform(new DOMSource(root), new StreamResult(stream))
        }
    }

    Document getDocument() {
        root
    }

    private Document parseSourceFile(File sourceFile) {
        documentBuilder().parse(sourceFile)
    }

    private DocumentBuilder documentBuilder() {
        DocumentBuilderFactory.newInstance().with {
            namespaceAware = true
            XIncludeAware = true
            newDocumentBuilder()
        }
    }
}
