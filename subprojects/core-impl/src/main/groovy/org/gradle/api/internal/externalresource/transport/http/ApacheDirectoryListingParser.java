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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApacheDirectoryListingParser {
    private static final Pattern PATTERN = Pattern.compile(
            "<a[^>]*href=\"([^\"]*)\"[^>]*>(?:<[^>]+>)*?([^<>]+?)(?:<[^>]+>)*?</a>",
            Pattern.CASE_INSENSITIVE);

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheDirectoryListingParser.class);
    private final URL inputUrl;

    public ApacheDirectoryListingParser(URL inputUrl) {
        this.inputUrl = inputUrl;
    }

    public List<URL> parse(String htmlText) throws IOException {
        List<URL> urlList = new ArrayList<URL>();
        Matcher matcher = PATTERN.matcher(htmlText);
        while (matcher.find()) {
            // get the href text and the displayed text
            String href = matcher.group(1);
            String text = matcher.group(2);
            text = text.trim();
            URL child = parseLink(href, text);
            if (child != null) {
                urlList.add(child);
                LOGGER.debug("found URL=[" + child + "].");
            }
        }
        return urlList;
    }

    URL parseLink(String href, String text) throws MalformedURLException {
        href = stripBaseURL(href);
        href = skipParentUrl(href);
        href = convertRelativeHrefToUrl(href);
        href = convertAbsoluteHrefToRelative(href);
        href = isTruncatedText(text) ? handleTruncatedLink(href, text) : validateHrefAndTextEquals(href, text);
        return href != null ? new URL(inputUrl, href) : null;
    }

    private String validateHrefAndTextEquals(String href, String text) {
        if (href != null && text != null) {
            String strippedHref = stripOptionalSlash(href);
            String strippedText = stripOptionalSlash(text);
            if (strippedHref != null && !strippedHref.equalsIgnoreCase(strippedText)) {
                return null;
            }
        }
        return href;
    }

    private String convertRelativeHrefToUrl(String href) {
        if (href != null && href.startsWith("./")) {
            return href.substring("./".length());
        }
        return href;
    }

    private String stripOptionalSlash(String input) {
        return input != null && input.endsWith("/") ? input.substring(0, input.length() - 1) : input;
    }

    private String handleTruncatedLink(String href, String text) {
        if (text.endsWith("..>")) {
            // text is probably truncated, we can only check if the href starts with text
            if (!href.startsWith(text.substring(0, text.length() - 3))) {
                return null;
            }
        } else if (text.endsWith("..&gt;")) {
            // text is probably truncated, we can only check if the href starts with text
            if (!href.startsWith(text.substring(0, text.length() - 6))) {
                return null;
            }
        }
        return href;
    }

    private boolean isTruncatedText(String text) {
        return text.endsWith("..>") || text.endsWith("..&gt;");
    }

    private String convertAbsoluteHrefToRelative(String href) {
        if (href != null && href.startsWith("/")) {
            int slashIndex = href.substring(0, href.length() - 1).lastIndexOf('/');
            return href.substring(slashIndex + 1);
        }
        return href;
    }

    private String skipParentUrl(String href) {
        if ("../".equals(href)) {
            return null;
        }
        return href;
    }

    private String stripBaseURL(String href) {
        if(href != null && (href.startsWith("http:") || href.startsWith("https:"))) {
            try {
                href = new URL(href).getPath();
                if (!href.startsWith(inputUrl.getPath())) {
                    return null;
                }
                return href.substring(inputUrl.getPath().length());
            } catch (Exception ignore) {
                return null;
            }
        }
        return href;
    }
}