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
import org.gradle.api.provider.Provider;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Manifest printer that produces {@code MANIFEST.MF} files.
 * Newlines are always printed as CRLF for reproducibility reasons.
 */
public class ManifestPrinter implements Closeable, Flushable {
    private final ManifestWriter out;

    public ManifestPrinter(ManifestWriter out) {
        this.out = out;
    }

    /**
     * Generates manifest printer that outputs the manifest to a given {@link Writer}.
     * @param out output writer
     */
    public ManifestPrinter(Writer out) {
        this(new ManifestWriter(out));
    }

    /**
     * Generates manifest printer that outputs the manifest to a given {@link OutputStream}.
     * The manifest is printed with {@link StandardCharsets#UTF_8} encoding.
     * @param out output stream
     */
    public ManifestPrinter(OutputStream out) {
        this(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Prints the manifest.
     * @param manifest manifest to print
     * @throws IOException if write fails
     */
    public void print(org.gradle.api.java.archives.Manifest manifest) throws IOException {
        writeMainSection(manifest.getAttributes());
        out.newLine();
        for (Map.Entry<String, Attributes> entry : manifest.getSections().entrySet()) {
            out.entry("Name", entry.getKey());
            DefaultAttributes attributes = (DefaultAttributes) entry.getValue();
            writeSection(attributes);
            out.newLine();
        }
    }

    private void writeMainSection(Attributes attributes) throws IOException {
        writeSectionTo(true, attributes);
    }

    private void writeSection(Attributes attributes) throws IOException {
        writeSectionTo(false, attributes);
    }

    private void writeSectionTo(boolean main, Attributes attributes) throws IOException {
        String versionKey = null;
        Object manifestVersion = null;
        if (main) {
            versionKey = java.util.jar.Attributes.Name.MANIFEST_VERSION.toString();
            manifestVersion = attributes.get(versionKey);
            if (manifestVersion == null) {
                versionKey = java.util.jar.Attributes.Name.SIGNATURE_VERSION.toString();
                manifestVersion = attributes.get(versionKey);
            }
        }

        if (manifestVersion != null) {
            out.entry(versionKey, resolveValueToString(manifestVersion));
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (main && key.equals(versionKey)) {
                continue;
            }
            out.entry(key, resolveValueToString(entry.getValue()));
        }
    }

    private static String resolveValueToString(Object value) {
        Object underlyingValue = value;
        if (value instanceof Provider) {
            underlyingValue = ((Provider) value).get();
        }
        return underlyingValue.toString();
    }
}
