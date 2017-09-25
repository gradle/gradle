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

package org.gradle.test.fixtures

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

class GradleModuleMetadata {
    private Map<String, Object> values
    private List<Variant> variants

    GradleModuleMetadata(TestFile file) {
        file.withReader { r ->
            JsonReader reader = new JsonReader(r)
            values = readObject(reader)
        }
        assert values.formatVersion == '0.1'
        assert values.createdBy.gradle.version == GradleVersion.current().version
        assert values.createdBy.gradle.buildId
        variants = (values.variants ?: []).collect { new Variant(it.name, it) }
    }

    List<Variant> getVariants() {
        return variants
    }

    Variant variant(String name) {
        def matches = variants.findAll { it.name == name }
        assert matches.size() == 1
        return matches.first()
    }

    private Map<String, Object> readObject(JsonReader reader) {
        Map<String, Object> values = [:]
        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            String name = reader.nextName()
            Object value = readAny(reader)
            values.put(name, value)
        }
        reader.endObject()
        return values
    }

    private Object readAny(JsonReader reader) {
        Object value = null
        switch (reader.peek()) {
            case JsonToken.STRING:
                value = reader.nextString()
                break
            case JsonToken.BEGIN_OBJECT:
                value = readObject(reader)
                break
            case JsonToken.BEGIN_ARRAY:
                value = readArray(reader)
                break
        }
        value
    }

    private List<Object> readArray(JsonReader reader) {
        List<Object> values = []
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            Object value = readAny(reader)
            values.add(value)
        }
        reader.endArray()
        return values
    }

    static class Variant {
        final String name
        private final Map<String, Object> values

        Variant(String name, Map<String, Object> values) {
            this.name = name
            this.values = values
        }

        List<?> getFiles() {
            return (values.files ?: []).collect { new File(it.name, it.url) }
        }
    }

    static class File {
        final String name
        final String url

        File(String name, String url) {
            this.name = name
            this.url = url
        }
    }
}
