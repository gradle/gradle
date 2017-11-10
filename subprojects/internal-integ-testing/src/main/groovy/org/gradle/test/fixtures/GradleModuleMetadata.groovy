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
import org.gradle.internal.hash.HashValue
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import javax.annotation.Nullable

class GradleModuleMetadata {
    private Map<String, Object> values
    private List<Variant> variants

    GradleModuleMetadata(TestFile file) {
        file.withReader { r ->
            JsonReader reader = new JsonReader(r)
            values = readObject(reader)
        }
        assert values.formatVersion == '0.2'
        assert values.createdBy.gradle.version == GradleVersion.current().version
        assert values.createdBy.gradle.buildId
        variants = (values.variants ?: []).collect { new Variant(it.name, it) }
    }

    @Nullable
    Coords getComponent() {
        def comp = values.component
        if (comp == null || comp.url) {
            return null
        }
        return new Coords(comp.group, comp.module, comp.version)
    }

    @Nullable
    ModuleReference getOwner() {
        def comp = values.component
        if (comp == null || !comp.url) {
            return null
        }
        return new ModuleReference(comp.group, comp.module, comp.version, comp.url)
    }

    List<Variant> getVariants() {
        return variants
    }

    Variant variant(String name) {
        def matches = variants.findAll { it.name == name }
        assert matches.size() == 1 : "Variant '$name' not found"
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
            case JsonToken.NULL:
                reader.nextNull()
                break
            case JsonToken.STRING:
                value = reader.nextString()
                break
            case JsonToken.BOOLEAN:
                value = reader.nextBoolean()
                break
            case JsonToken.NUMBER:
                value = reader.nextLong()
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

        @Nullable
        ModuleReference getAvailableAt() {
            def ref = values['available-at']
            return ref == null ? null : new ModuleReference(ref.group, ref.module, ref.version, ref.url)
        }

        List<Dependency> getDependencies() {
            return (values.dependencies ?: []).collect { new Dependency(it.group, it.module, it.version.prefers, it.version.rejects?:[]) }
        }

        List<File> getFiles() {
            return (values.files ?: []).collect { new File(it.name, it.url, it.size, new HashValue(it.sha1), new HashValue(it.md5)) }
        }
    }

    static class Coords {
        final String group
        final String module
        final String version
        final List<String> rejectsVersion

        Coords(String group, String module, String version, List<String> rejectsVersion = []) {
            this.group = group
            this.module = module
            this.version = version
            this.rejectsVersion = rejectsVersion
        }

        String getCoords() {
            return "$group:$module:${version ?: ''}"
        }
    }

    static class ModuleReference extends Coords {
        final String url

        ModuleReference(String group, String module, String version, String url) {
            super(group, module, version)
            this.url = url
        }
    }

    static class Dependency extends Coords {
        Dependency(String group, String module, String version, List<String> rejectedVersions) {
            super(group, module, version, rejectedVersions)
        }
    }

    static class File {
        final String name
        final String url
        final long size
        final HashValue sha1
        final HashValue md5

        File(String name, String url, long size, HashValue sha1, HashValue md5) {
            this.name = name
            this.url = url
            this.size = size
            this.sha1 = sha1
            this.md5 = md5
        }
    }
}
