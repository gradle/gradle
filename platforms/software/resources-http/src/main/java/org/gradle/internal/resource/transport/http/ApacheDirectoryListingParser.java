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

package org.gradle.internal.resource.transport.http;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.UriTextResource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ApacheDirectoryListingParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheDirectoryListingParser.class);

    public List<String> parse(URI baseURI, InputStream content, String contentType) throws Exception {
        baseURI = addTrailingSlashes(baseURI);
        if (contentType == null || !contentType.startsWith("text/html")) {
            throw new ResourceException(baseURI, String.format("Unsupported ContentType %s for directory listing '%s'", contentType, baseURI));
        }
        Charset contentEncoding = UriTextResource.extractCharacterEncoding(contentType, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(content, contentEncoding.name(), baseURI.toString());
        Elements elements = document.select("a[href]");
        List<String> hrefs = elements.stream()
            .map(it -> it.attr("href"))
            .collect(Collectors.toList());
        List<URI> uris = resolveURIs(baseURI, hrefs);
        return filterNonDirectChilds(baseURI, uris);
    }

    private URI addTrailingSlashes(URI uri) throws IOException, URISyntaxException {
        if(uri.getPath() == null){
            uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), "/", uri.getQuery(), uri.getFragment());
        }else if (!uri.getPath().endsWith("/") && !uri.getPath().endsWith(".html")) {
            uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath() + "/", uri.getQuery(), uri.getFragment());

        }
        return uri;
    }

    private List<String> filterNonDirectChilds(URI baseURI, List<URI> inputURIs) throws MalformedURLException {
        final int baseURIPort = baseURI.getPort();
        final String baseURIHost = baseURI.getHost();
        final String baseURIScheme = baseURI.getScheme();

        List<String> uris = new ArrayList<String>();
        final String prefixPath = baseURI.getPath();
        for (URI parsedURI : inputURIs) {
            if (parsedURI.getHost() != null && !parsedURI.getHost().equals(baseURIHost)) {
                continue;
            }
            if (parsedURI.getScheme() != null && !parsedURI.getScheme().equals(baseURIScheme)) {
                continue;
            }
            if (parsedURI.getPort() != baseURIPort) {
                continue;
            }
            if (parsedURI.getPath() != null && !parsedURI.getPath().startsWith(prefixPath)) {
                continue;
            }
            String childPathPart = parsedURI.getPath().substring(prefixPath.length(), parsedURI.getPath().length());
            if (childPathPart.startsWith("../")) {
                continue;
            }
            if (childPathPart.equals("") || childPathPart.split("/").length > 1) {
                continue;
            }

            String path = parsedURI.getPath();
            int pos = path.lastIndexOf('/');
            if (pos < 0) {
                uris.add(path);
            } else if (pos == path.length() - 1) {
                int start = path.lastIndexOf('/', pos - 1);
                if (start < 0) {
                    uris.add(path.substring(0, pos));
                } else {
                    uris.add(path.substring(start + 1, pos));
                }
            } else {
                uris.add(path.substring(pos + 1));
            }
        }
        return uris;
    }

    private List<URI> resolveURIs(URI baseURI, List<String> hrefs) {
        List<URI> uris = new ArrayList<>();
        for (String href : hrefs) {
            try {
                uris.add(baseURI.resolve(href));
            } catch (IllegalArgumentException ex) {
                LOGGER.debug("Cannot resolve anchor: {}", href);
            }
        }
        return uris;
    }
}
