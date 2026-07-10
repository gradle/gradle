/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.publish.internal.metadata;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.List;

/**
 * Simplifies the task of writing to a JsonWriter.
 */
abstract class JsonWriterScope {

    protected interface Contents {
        void write() throws IOException;
    }

    private final JsonWriter jsonWriter;

    protected JsonWriterScope(JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    protected void writeArray(String name, List<String> elements) throws IOException {
        writeArray(name, () -> {
            for (String element : elements) {
                jsonWriter.value(element);
            }
        });
    }

    protected void writeArray(String name, Contents contents) throws IOException {
        jsonWriter.name(name);
        writeArray(contents);
    }

    protected void writeArray(Contents contents) throws IOException {
        jsonWriter.beginArray();
        contents.write();
        endArray();
    }

    protected void beginArray(String name) throws IOException {
        jsonWriter.name(name);
        jsonWriter.beginArray();
    }

    protected void endArray() throws IOException {
        jsonWriter.endArray();
    }

    protected void writeObject(String name, Contents contents) throws IOException {
        jsonWriter.name(name);
        writeObject(contents);
    }

    protected void writeObject(Contents contents) throws IOException {
        jsonWriter.beginObject();
        contents.write();
        jsonWriter.endObject();
    }

    protected void write(String name, Number number) throws IOException {
        jsonWriter.name(name).value(number);
    }

    protected void write(String name, long length) throws IOException {
        jsonWriter.name(name).value(length);
    }

    protected void write(String name, boolean value) throws IOException {
        jsonWriter.name(name).value(value);
    }

    protected void write(String name, String value) throws IOException {
        jsonWriter.name(name).value(value);
    }
}
