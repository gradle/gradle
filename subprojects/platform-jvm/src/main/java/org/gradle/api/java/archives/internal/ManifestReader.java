/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal;

import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses a {@code MANIFEST.MF} contents into {@link Manifest} format.
 */
public class ManifestReader implements Closeable {
    private static final char NEWLINE = '\n';
    private static final int CONTEXT_SIZE = 30;
    // This is to prevent "OutOfMemory" when too large value appears somehow
    private static final int MAX_VALUE_SIZE = 20_000_000;
    private final Reader reader;
    private final StringBuilder sb = new StringBuilder();

    private char[] buffer = new char[1024];
    private int first; // inclusive
    private int last; // exclusive
    private boolean eofSeen;

    /**
     * Creates a manifest reader. Input sequence must use {@code '\n'} for newlines,
     * and it must not contain continuations.
     * The above processing can be performed with {@link ManifestInputStream}
     * @param reader input reader
     */
    public ManifestReader(Reader reader) {
        this.reader = reader;
    }

    /**
     * Creates a manifest reader with {@link StandardCharsets#UTF_8} encoding.
     * @param in input stream
     */
    public ManifestReader(ManifestInputStream in) {
        this(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private String readKey() throws IOException {
        String key = readTill(':');
        skipSpace();
        return key;
    }

    private String readValue() throws IOException {
        String value = readTill(NEWLINE);
        skipNewline();
        return value;
    }

    /**
     * Reads a manifest and adds the attributes to a given {@link Manifest} object.
     * @param m manifest that will receive attributes
     * @throws IOException if read fails or invalid manifest format detected
     */
    public void read(Manifest m) throws IOException {
        String currentSectionName = null;
        Map<String, String> currentSection = null;

        Attributes mainAttributes = m.getAttributes();
        while (true) {
            // Newline is optional. For instance, it might precede the start of the section (before "Name: sectionName")
            skipNewline();
            if (isEof()) {
                if (currentSectionName != null) {
                    m.attributes(currentSection, currentSectionName);
                }
                break;
            }
            String key = readKey();
            String value = readValue();
            if (!key.equalsIgnoreCase("Name")) {
                if (currentSectionName == null) {
                    if (mainAttributes.isEmpty()
                        && !java.util.jar.Attributes.Name.MANIFEST_VERSION.toString().equalsIgnoreCase(key)
                        && !java.util.jar.Attributes.Name.SIGNATURE_VERSION.toString().equalsIgnoreCase(key)) {
                        mainAttributes.put(java.util.jar.Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
                    }
                    mainAttributes.put(key, value);
                } else {
                    currentSection.put(key, value);
                }
                continue;
            }
            // Name detected => new section
            if (value.equals(currentSectionName)) {
                // The section is the same => no action
                continue;
            }
            // Switch to a Section
            if (currentSection == null) {
                currentSection = new LinkedHashMap<>();
            } else {
                m.attributes(currentSection, currentSectionName);
                currentSection.clear();
            }
            currentSectionName = value;
        }
        // Does not reach here
    }

    private IOException newParseError(String message, int offset) {
        StringBuilder sb = new StringBuilder(message);
        // Append contents from the buffer for more meaningful message
        sb.append("\nContext: ");
        if (last - first == 0 || offset < first || offset > last) {
            sb.append("not available");
        } else {
            int contextStart = Math.max(0, offset - CONTEXT_SIZE);
            sb.append(buffer, contextStart, offset - contextStart);
            sb.append("<error here>");
            int contextEnd = Math.min(last, offset + CONTEXT_SIZE);
            sb.append(buffer, offset, contextEnd - offset);
            System.out.println(new String(buffer, first, last-first));
        }
        return new IOException(sb.toString());
    }

    private boolean isEof() throws IOException {
        if (last != first) {
            return false;
        }
        fill();
        return eofSeen && last == first;
    }

    private void fill() throws IOException {
        // Ensure the buffer contains at least 10 chars
        int currentChars = last - first;
        if (currentChars == 0) {
            last = 0;
        } else {
            if (currentChars > 10) {
                return;
            }
            // Too few chars in the buffer. De-fragment the buffer and read more
            System.arraycopy(buffer, first, buffer, 0, last - first);
            last -= first;
        }
        first = 0;
        // Fill the rest of the buffer
        int read = 0;
        for (int i = 0; i < 42 && read == 0; i++) {
            read = reader.read(buffer, last, buffer.length - last);
        }
        if (read == 0) {
            throw new IllegalStateException(reader + ".read(char[" + buffer.length + "], " + last + "," + (buffer.length - last) + ") produces 0");
        }
        if (read == -1) {
            eofSeen = true;
            return;
        }
        last += read;
    }

    private String readTill(char delim) throws IOException {
        sb.setLength(0);
        char[] buffer = this.buffer;
        while(true) {
            fill();
            int first = this.first;
            int last = this.last;
            if (first == last && eofSeen) {
                throw newParseError("End of manifest detected while searching for '" + delim + "'. Parsed value so far is '" + sb.toString() + "'", -1);
            }
            for (int i = first; i < last; i++) {
                if (buffer[i] != delim) {
                    continue;
                }
                // Delimiter detected
                String result;
                if (sb.length() == 0) {
                    // We've found the match at the first try
                    result = new String(buffer, first, i - first);
                } else {
                    // StringBuilder contains previous characters
                    result = sb.append(buffer, first, i - first).toString();
                }
                // Skip the delimiter itself, thus +1
                this.first = i + 1;
                return result;
            }
            sb.append(buffer, first, last - first);
            this.first = last;
            if (sb.length() > MAX_VALUE_SIZE) {
                throw newParseError("Manifest value exceeds " + MAX_VALUE_SIZE + ": " + sb.substring(0, 1000) + "...", -1);
            }
        }
    }

    private void skipNewline() throws IOException {
        fill();
        int first = this.first;
        int last = this.last;
        if (first < last && buffer[first] == NEWLINE) {
            this.first = first + 1;
        }
    }

    private void skipSpace() throws IOException {
        fill();
        int first = this.first;
        int last = this.last;
        char[] buffer = this.buffer;
        if (first < last && buffer[first] == ' ') {
            first++;
        } else {
            throw newParseError("Colon should be followed by a space", first);
        }
        this.first = first;
    }
}
