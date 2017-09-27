/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.gradle.api.Transformer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.model.MutableModuleMetadata;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.google.gson.stream.JsonToken.END_OBJECT;

public class ModuleMetadataParser {
    public static final String FORMAT_VERSION = "0.1";
    private final ImmutableAttributesFactory attributesFactory;

    public ModuleMetadataParser(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    public void parse(final LocallyAvailableExternalResource resource, final MutableModuleMetadata metadata) {
        resource.withContent(new Transformer<Void, InputStream>() {
            @Override
            public Void transform(InputStream inputStream) {
                try {
                    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, "utf-8"));
                    reader.beginObject();
                    if (reader.peek() != JsonToken.NAME) {
                        throw new RuntimeException("Module metadata should contain a 'formatVersion' value.");
                    }
                    String name = reader.nextName();
                    if (!name.equals("formatVersion")) {
                        throw new RuntimeException(String.format("The 'formatVersion' value should be the first value in a module metadata. Found '%s' instead.", name));
                    }
                    if (reader.peek() != JsonToken.STRING) {
                        throw new RuntimeException("The 'formatVersion' value should have a string value.");
                    }
                    String version = reader.nextString();
                    if (!version.equals(FORMAT_VERSION)) {
                        throw new RuntimeException(String.format("Unsupported format version '%s' specified in module metadata. This version of Gradle supports only format version %s.", version, FORMAT_VERSION));
                    }
                    consumeTopLevelElements(reader, metadata);
                    return null;
                } catch (Exception e) {
                    throw new MetaDataParseException("module metadata", resource, e);
                }
            }
        });
    }

    private void consumeTopLevelElements(JsonReader reader, MutableModuleMetadata metadata) throws IOException {
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if (name.equals("variants")) {
                consumeVariants(reader, metadata);
            } else {
                consumeAny(reader);
            }
        }
    }

    private void consumeVariants(JsonReader reader, MutableModuleMetadata metadata) throws IOException {
        reader.beginArray();
        while (reader.peek() != JsonToken.END_ARRAY) {
            consumeVariant(reader, metadata);
        }
        reader.endArray();
    }

    private void consumeVariant(JsonReader reader, MutableModuleMetadata metadata) throws IOException {
        reader.beginObject();
        String variantName = null;
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if (name.equals("name")) {
                variantName = reader.nextString();
            } else if (name.equals("attributes")) {
                attributes = consumeAttributes(reader);
            } else {
                consumeAny(reader);
            }
        }
        reader.endObject();
        metadata.addVariant(variantName, attributes);
    }

    private ImmutableAttributes consumeAttributes(JsonReader reader) throws IOException {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        reader.beginObject();
        while(reader.peek()!= END_OBJECT) {
            String attrName = reader.nextName();
            String attrValue = reader.nextString();
            attributes = attributesFactory.concat(attributes, Attribute.of(attrName, String.class), attrValue);
        }
        reader.endObject();
        return attributes;
    }

    private void consumeAny(JsonReader reader) throws IOException {
        reader.skipValue();
    }
}
