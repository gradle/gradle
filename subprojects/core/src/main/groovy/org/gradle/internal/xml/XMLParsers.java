/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.xml;

import com.ctc.wstx.sax.WstxSAXParserFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;

public class XMLParsers {
    private static final SAXParserFactory NON_VALIDATING_SAX_PARSER_FACTORY = createWoodstoxSaxParserFactory();

    private static WstxInputFactory createWoodstoxXmlInputFactory() {
        WstxInputFactory inputFactory = new WstxInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return inputFactory;
    }

    private static WstxSAXParserFactory createWoodstoxSaxParserFactory() {
        WstxSAXParserFactory factory = new WstxSAXParserFactory(createWoodstoxXmlInputFactory());
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    public static SAXParser createNonValidatingSaxParser() throws ParserConfigurationException, SAXException {
        return NON_VALIDATING_SAX_PARSER_FACTORY.newSAXParser();
    }

    public static XMLInputFactory createNonValidatingXMLInputFactory() {
        return createWoodstoxXmlInputFactory();
    }
}
