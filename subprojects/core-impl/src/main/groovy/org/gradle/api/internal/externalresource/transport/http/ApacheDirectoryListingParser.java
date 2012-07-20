/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.transport.http;

import org.cyberneko.html.parsers.SAXParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApacheDirectoryListingParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheDirectoryListingParser.class);
    private final URI baseURI;

    public ApacheDirectoryListingParser(URI baseURI) {
        this.baseURI = baseURI;
    }

    public List<URI> parse(byte[] content, String encoding) throws IOException {
        final String htmlText = new String(content); //TODO: converting with correct encoding
        final InputSource inputSource = new InputSource(new StringReader(htmlText));
        final SAXParser htmlParser = new SAXParser();
        final AnchorListerHandler anchorListerHandler = new AnchorListerHandler();
        htmlParser.setContentHandler(anchorListerHandler);
        try {
            htmlParser.parse(inputSource);
        } catch (SAXException e) {
            LOGGER.warn(String.format("Unable to parse DirectoryListing for %s"), e);
            return Collections.emptyList();
        }

        List<String> hrefs = anchorListerHandler.getHrefs();
        List<URI> uris = resolveURIs(baseURI, hrefs);
        return filterNonDirectChilds(baseURI, uris);
    }

    private List<URI> filterNonDirectChilds(URI baseURI, List<URI> inputURIs) throws MalformedURLException {
        final int baseURIPort = baseURI.getPort();
        final String baseURIHost = baseURI.getHost();
        final String baseURIScheme = baseURI.getScheme();

        List<URI> uris = new ArrayList<URI>();
        final String prefixPath = baseURI.getPath();
        for (URI parsedURI : inputURIs) {
            if (!parsedURI.getHost().equals(baseURIHost)) {
                continue;
            }
            if (!parsedURI.getScheme().equals(baseURIScheme)) {
                continue;
            }
            if (parsedURI.getPort() != baseURIPort) {
                continue;
            }
            if (!parsedURI.getPath().startsWith(prefixPath)) {
                continue;
            }
            String childPathPart = parsedURI.getPath().substring(prefixPath.length(), parsedURI.getPath().length());
            if(childPathPart.startsWith("../")){
                continue;
            }
            if(childPathPart.equals("") || childPathPart.split("/").length>1){
                continue;
            }

            uris.add(parsedURI);
        }
        return uris;
    }

    private List<URI> resolveURIs(URI baseURI, List<String> hrefs) {
        List<URI> uris = new ArrayList<URI>();
        for (String href : hrefs) {
            try {
                uris.add(baseURI.resolve(href));
            } catch (IllegalArgumentException ex) {
                LOGGER.debug(String.format("Cannot resolve anchor: %s", href));
            }
        }
        return uris;
    }

    private class AnchorListerHandler implements ContentHandler {
        List<String> hrefs = new ArrayList<String>();

        public List<String> getHrefs() {
            return hrefs;
        }

        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (qName.equalsIgnoreCase("A")) {
                final String href = atts.getValue("href");
                if (href != null) {
                    hrefs.add(href);
                }
            }
        }

        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument() throws SAXException {
        }

        public void endDocument() throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
        }

        public void endPrefixMapping(String prefix) throws SAXException {
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
        }

        public void characters(char[] ch, int start, int length) throws SAXException {
        }

        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        }

        public void processingInstruction(String target, String data) throws SAXException {
        }

        public void skippedEntity(String name) throws SAXException {
        }
    }
}