/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.provider.Provider;
import org.gradle.internal.UncheckedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Represents a Manifest that knows the character set of its content and can write to an OutputStream using that character set.
 *
 * @since 2.14
 */
public interface ManifestInternal extends Manifest {
    String DEFAULT_CONTENT_CHARSET = "UTF-8";

    /**
     * Writes the manifest into a stream.
     *
     * The manifest will be encoded using the character set implicit in the manifest itself.
     *
     * @param outputStream The stream to write the manifest to
     * @return this
     */
    Manifest writeTo(OutputStream outputStream);

    class ManifestWriter {
        private static java.util.jar.Manifest generateJavaManifest(Manifest gradleManifest) {
            java.util.jar.Manifest javaManifest = new java.util.jar.Manifest();
            addMainAttributesToJavaManifest(gradleManifest, javaManifest);
            addSectionAttributesToJavaManifest(gradleManifest, javaManifest);
            return javaManifest;
        }

        private static void addMainAttributesToJavaManifest(Manifest gradleManifest, java.util.jar.Manifest javaManifest) {
            fillAttributes(gradleManifest.getAttributes(), javaManifest.getMainAttributes());
        }

        private static void addSectionAttributesToJavaManifest(Manifest gradleManifest, java.util.jar.Manifest javaManifest) {
            for (Map.Entry<String, Attributes> entry : gradleManifest.getSections().entrySet()) {
                String sectionName = entry.getKey();
                java.util.jar.Attributes targetAttributes = new java.util.jar.Attributes();
                fillAttributes(entry.getValue(), targetAttributes);
                javaManifest.getEntries().put(sectionName, targetAttributes);
            }
        }

        private static void fillAttributes(Attributes attributes, java.util.jar.Attributes targetAttributes) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String mainAttributeName = entry.getKey();
                String mainAttributeValue = resolveValueToString(entry.getValue());
                if (mainAttributeValue != null) {
                    targetAttributes.putValue(mainAttributeName, mainAttributeValue);
                }
            }
        }

        private static String resolveValueToString(Object value) {
            Object underlyingValue = value;
            if (value instanceof Provider) {
                Provider<?> provider = uncheckedCast(value);
                if (!provider.isPresent()) {
                    return null;
                }
                underlyingValue = provider.get();
            }
            return underlyingValue.toString();
        }

        static void writeTo(Manifest manifest, OutputStream outputStream, String contentCharset) {
            try {
                java.util.jar.Manifest javaManifest = generateJavaManifest(manifest.getEffectiveManifest());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                javaManifest.write(buffer);
                byte[] manifestBytes;
                if (DEFAULT_CONTENT_CHARSET.equals(contentCharset)) {
                    manifestBytes = buffer.toByteArray();
                } else {
                    // Convert the UTF-8 manifest bytes to the requested content charset
                    manifestBytes = buffer.toString(DEFAULT_CONTENT_CHARSET).getBytes(contentCharset);
                }
                outputStream.write(manifestBytes);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
