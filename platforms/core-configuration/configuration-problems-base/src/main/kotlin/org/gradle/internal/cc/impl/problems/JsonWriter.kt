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

import org.apache.groovy.json.internal.CharBuf
import java.io.Writer

class JsonWriter(private val writer: Writer) {
    private var nextItemNeedsComma = false

    fun jsonObject(body: () -> Unit) {
        beginObject()
        body()
        endObject()
    }

    fun beginObject() {
        increaseLevel()
        write('{')
    }

    fun endObject() {
        write('}')
        decreaseLevel()
    }

    fun beginArray() {
        increaseLevel()
        write('[')
    }

    fun endArray() {
        write(']')
        decreaseLevel()
    }


    fun property(name: String, value: String) {
        property(name) { jsonString(value) }
    }

    fun property(name: String, value: Int) {
        property(name) { write(value.toString()) }
    }

    fun property(name: String, value: () -> Unit) {
        elementSeparator()
        propertyName(name)
        increaseLevel()
        value()
        decreaseLevel()
    }

    fun propertyName(name: String) {
        simpleString(name)
        write(':')
    }

    fun write(csq: CharSequence) {
        writer.append(csq)
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

    private
    fun elementSeparator() {
        if (nextItemNeedsComma) {
            comma()
        } else {
            nextItemNeedsComma = true
        }
    }

    private
    fun comma() {
        write(',')
    }

    private
    fun increaseLevel() {
        nextItemNeedsComma = false
    }

    private
    fun decreaseLevel() {
        nextItemNeedsComma = true
    }

    private
    fun simpleString(name: String) {
        write('"')
        write(name)
        write('"')
    }

    private
    fun jsonString(value: String) {
        if (value.isEmpty()) {
            write("\"\"")
        } else {
            buffer.addJsonEscapedString(value)
            write(buffer.toStringAndRecycle())
        }
    }

    private
    val buffer = CharBuf.create(255)

    private
    fun write(c: Char) = writer.append(c)

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
        elementSeparator()
        increaseLevel()
        body()
        decreaseLevel()
    }
}
