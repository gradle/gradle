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
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ModuleMetadataParser {
    public static final String FORMAT_VERSION = "0.1";

    public void parse(final LocallyAvailableExternalResource resource) {
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
                    consumeToEndOfObject(reader);
                    return null;
                } catch (Exception e) {
                    throw new MetaDataParseException("module metadata", resource, e);
                }
            }
        });
    }

    private void consumeAny(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case STRING:
                reader.nextString();
                break;
            case BEGIN_OBJECT:
                consumeObject(reader);
                break;
            case BEGIN_ARRAY:
                consumeArray(reader);
                break;
        }
    }

    private void consumeObject(JsonReader reader) throws IOException {
        reader.beginObject();
        consumeToEndOfObject(reader);
    }

    private void consumeToEndOfObject(JsonReader reader) throws IOException {
        while (reader.peek() != JsonToken.END_OBJECT) {
            reader.nextName();
            consumeAny(reader);
        }
        reader.endObject();
    }

    private void consumeArray(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.peek() != JsonToken.END_ARRAY) {
            consumeAny(reader);
        }
        reader.endArray();
    }
}
