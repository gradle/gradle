/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

/**
 * Factories for javax.xml.
 */
@NonNullApi
public final class XmlFactories {

    public static DocumentBuilderFactory newDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return dbf;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to create secure DocumentBuilderFactory", e);
        }
    }

    public static SAXParserFactory newSAXParserFactory() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return spf;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to create secure SAXParserFactory", e);
        } catch (SAXNotSupportedException e) {
            throw new RuntimeException("Unable to create secure SAXParserFactory", e);
        } catch (SAXNotRecognizedException e) {
            throw new RuntimeException("Unable to create secure SAXParserFactory", e);
        }
    }

    public static XPathFactory newXPathFactory() {
        try {
            XPathFactory xpf = XPathFactory.newInstance();
            xpf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return xpf;
        } catch (XPathFactoryConfigurationException e) {
            throw new RuntimeException("Unable to create secure XPathFactory", e);
        }
    }

    public static TransformerFactory newTransformerFactory() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return tf;
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException("Unable to create secure TransformerFactory", e);
        }
    }

    private XmlFactories() {}
}
