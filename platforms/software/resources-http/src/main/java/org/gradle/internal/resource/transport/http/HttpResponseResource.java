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
package org.gradle.internal.resource.transport.http;

import org.apache.http.HttpHeaders;
import org.apache.http.client.utils.DateUtils;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HttpResponseResource implements ExternalResourceReadResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResponseResource.class);

    private final String method;
    private final URI source;
    private final HttpClient.Response response;
    private final ExternalResourceMetaData metaData;
    private boolean wasOpened;

    public HttpResponseResource(String method, URI source, HttpClient.Response response) {
        this.method = method;
        this.source = source;
        this.response = response;

        String etag = getEtag(response);
        this.metaData = new DefaultExternalResourceMetaData(source, getLastModified(), getContentLength(), getContentType(), etag, getSha1(response, etag), getFilename(), response.isMissing());
    }

    public URI getURI() {
        return source;
    }

    @Override
    public String toString() {
        return "Http " + method + " Resource: " + source;
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    public int getStatusCode() {
        return response.getStatusCode();
    }

    public Date getLastModified() {
        String responseHeader = response.getHeader(HttpHeaders.LAST_MODIFIED);
        if (responseHeader == null) {
            return new Date(0);
        }
        try {
            return DateUtils.parseDate(responseHeader);
        } catch (Exception e) {
            return new Date(0);
        }
    }

    private String getFilename() {
        String disposition = response.getHeader("Content-Disposition");
        if (disposition != null) {
            String fromHeader = extractFilenameFromContentDisposition(disposition);
            if (fromHeader != null && !fromHeader.isEmpty()) {
                return fromHeader;
            }
        }

        URI effectiveUri = response.getEffectiveUri() != null ? response.getEffectiveUri() : source;
        return extractFilenameFromUri(effectiveUri);
    }

    private static String extractFilenameFromUri(URI uri) {
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            int lastSlash = path.lastIndexOf('/');
            String candidate = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        String rawUri = uri.toString();
        int lastSlash = rawUri.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < rawUri.length()) {
            String candidate = rawUri.substring(lastSlash + 1);
            int queryStart = candidate.indexOf('?');
            if (queryStart >= 0) {
                candidate = candidate.substring(0, queryStart);
            }
            int fragmentStart = candidate.indexOf('#');
            if (fragmentStart >= 0) {
                candidate = candidate.substring(0, fragmentStart);
            }
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Parses the Content-Disposition header value to extract a filename.
     * Supports both {@code filename} (RFC 2183) and {@code filename*} (RFC 5987) parameters,
     * preferring {@code filename*} when both are present.
     */
    static String extractFilenameFromContentDisposition(String disposition) {
        if (disposition == null) {
            return null;
        }

        String filename = null;
        String filenameStar = null;

        for (String part : splitHeaderParameters(disposition)) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
                continue;
            }
            int equalsIndex = trimmedPart.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }

            String paramName = trimmedPart.substring(0, equalsIndex).trim().toLowerCase(Locale.ROOT);
            String rawValue = trimmedPart.substring(equalsIndex + 1).trim();
            String paramValue = unquoteAndUnescape(rawValue);

            if ("filename*".equals(paramName)) {
                String decoded = decodeRfc5987(paramValue);
                if (decoded != null && !decoded.isEmpty()) {
                    filenameStar = decoded;
                }
            } else if ("filename".equals(paramName)) {
                if (paramValue != null && !paramValue.isEmpty()) {
                    filename = paramValue;
                }
            }
        }

        return filenameStar != null ? filenameStar : filename;
    }

    /**
     * Splits a header value on semicolons, respecting quoted strings.
     */
    private static List<String> splitHeaderParameters(String headerValue) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < headerValue.length(); i++) {
            char ch = headerValue.charAt(i);

            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && inQuotes) {
                current.append(ch);
                escaped = true;
                continue;
            }

            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }

            if (ch == ';' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        parts.add(current.toString());
        return parts;
    }

    /**
     * Removes surrounding quotes and unescapes quoted-pairs (backslash-escaped characters).
     */
    private static String unquoteAndUnescape(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
            return trimmed;
        }

        String inner = trimmed.substring(1, trimmed.length() - 1);
        StringBuilder result = new StringBuilder(inner.length());
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (escaped) {
                result.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Decodes an RFC 5987 encoded value: {@code charset'language'percent-encoded-value}.
     */
    private static String decodeRfc5987(String value) {
        if (value == null) {
            return null;
        }
        int firstQuote = value.indexOf('\'');
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = value.indexOf('\'', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }

        String charsetName = value.substring(0, firstQuote).trim();
        if (charsetName.isEmpty()) {
            return null;
        }

        String encoded = value.substring(secondQuote + 1);
        try {
            byte[] bytes = percentDecodeToBytes(encoded);
            return new String(bytes, Charset.forName(charsetName));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] percentDecodeToBytes(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '%') {
                if (i + 2 >= input.length()) {
                    throw new IllegalArgumentException("Incomplete percent-encoding at index " + i);
                }
                int high = Character.digit(input.charAt(i + 1), 16);
                int low = Character.digit(input.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Invalid percent-encoding at index " + i);
                }
                output.write((high << 4) + low);
                i += 2;
            } else {
                output.write((byte) ch);
            }
        }
        return output.toByteArray();
    }

    public long getContentLength() {
        String header = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (header == null) {
            return -1;
        }

        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getHeaderValue(String name) {
        return response.getHeader(name);
    }

    public String getContentType() {
        return response.getHeader(HttpHeaders.CONTENT_TYPE);
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public InputStream openStream() throws IOException {
        if (wasOpened) {
            throw new IOException("Unable to open Stream as it was opened before.");
        }
        LOGGER.debug("Attempting to download resource {}.", source);
        this.wasOpened = true;
        return response.getContent();
    }

    @Override
    public void close() {
        response.close();
    }

    private static String getEtag(HttpClient.Response response) {
        return response.getHeader(HttpHeaders.ETAG);
    }

    private static HashCode getSha1(HttpClient.Response response, String etag) {
        String sha1Header = response.getHeader("X-Checksum-Sha1");
        if (sha1Header != null) {
            return HashCode.fromString(sha1Header);
        }

        // Nexus uses sha1 etags, with a constant prefix
        // e.g {SHA1{b8ad5573a5e9eba7d48ed77a48ad098e3ec2590b}}
        if (etag != null && etag.startsWith("{SHA1{")) {
            String hash = etag.substring(6, etag.length() - 2);
            return HashCode.fromString(hash);
        }

        return null;
    }
}
