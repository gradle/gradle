/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.classloader;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

public class DefaultClassLoaderFactoryTestHelper {
    public void doStuff() throws Exception {
        SAXParserFactory.newInstance().newSAXParser();
        DocumentBuilderFactory.newInstance().newDocumentBuilder();
        DatatypeFactory.newInstance().newXMLGregorianCalendar();
        TransformerFactory.newInstance().newTransformer();
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        XPathFactory.newInstance().newXPath();
    }
}
