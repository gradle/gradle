/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import com.fasterxml.jackson.core.JsonFactory
import java.io.Writer

class JsonWriter(private val writer: Writer) {

    class JsonObject(val jsonWriter: JsonWriter) : AutoCloseable {
        init {
            jsonWriter.beginObject()
        }

        override fun close() {
            jsonWriter.endObject()
        }
    }

    private
    val jsonGenerator = JsonFactory().createGenerator(writer)

    fun jsonObject(body: () -> Unit) {
        JsonObject(this).use {
            body()
        }
    }

    fun beginObject() {
        jsonGenerator.writeStartObject()
    }

    fun endObject() {
        jsonGenerator.writeEndObject()
    }

    fun beginArray() {
        jsonGenerator.writeStartArray()
    }

    fun endArray() {
        jsonGenerator.writeEndArray()
    }

    fun propertyName(name: String) {
        jsonGenerator.writeFieldName(name)
    }

    fun property(name: String, value: String) {
        jsonGenerator.writeStringField(name, value)
    }

    fun property(name: String, value: Int) {
        jsonGenerator.writeNumberField(name, value)
    }

    fun property(name: String, value: () -> Unit) {
        jsonGenerator.writeFieldName(name)
        value()
    }

    fun <T> jsonObjectList(list: Iterable<T>, body: (T) -> Unit) {
        jsonObjectList(list.iterator(), body)
    }

    private
    fun <T> jsonObjectList(list: Iterator<T>, body: (T) -> Unit) {
        jsonList(list) {
            jsonObject {
                body(it)
            }
        }
    }

    fun <T> jsonList(list: Iterable<T>, body: (T) -> Unit) {
        jsonList(list.iterator(), body)
    }

    private
    fun <T> jsonList(list: Iterator<T>, body: (T) -> Unit) {
        beginArray()
        list.forEach {
            jsonListItem {
                body(it)
            }
        }
        endArray()
    }

    fun jsonListItem(body: () -> Unit) {
        body()
    }

    fun flush() {
        jsonGenerator.flush()
    }
}
