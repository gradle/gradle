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

package org.gradle.internal.configuration.problems

import org.apache.groovy.json.internal.CharBuf
import org.gradle.api.internal.DocumentationRegistry
import java.io.Writer

open class JsonModelWriterCommon(val writer: Writer) {

    protected
    val documentationRegistry = DocumentationRegistry()

    // This is a map of booleans that indicate whether the next element in the array should be preceded by a comma.
    var arrayContext = mutableMapOf<Int, Boolean>()
    var level: Int = 0

    inline fun jsonObject(body: () -> Unit) {
        listSepatator()
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

    fun listSepatator() {
        if (arrayContext.containsKey(level)) {
            if (arrayContext.get(level) == true) {
                comma()
            } else {
                arrayContext[level] = true
            }
        }
    }

    private fun increaseLevel() {
        level++
        arrayContext[level] = false
    }

    private fun decreaseLevel() {
        arrayContext.remove(level)
        level--
    }

    fun endArray() {
        write(']')
        decreaseLevel()
    }


    fun property(name: String, value: String) {
        property(name) { jsonString(value) }
    }

    inline fun property(name: String, value: () -> Unit) {
        propertyName(name)
        value()
    }

    fun propertyName(name: String) {
        simpleString(name)
        write(':')
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

    fun comma() {
        write(',')
    }

    fun documentationLinkFor(section: DocumentationSection) =
        documentationRegistry.documentationLinkFor(section)

    private
    val buffer = CharBuf.create(255)

    protected
    fun write(csq: CharSequence) = writer.append(csq)

    private
    fun write(c: Char) = writer.append(c)

    inline fun <T> jsonObjectList(list: Iterable<T>, body: (T) -> Unit) {
        jsonList(list) {
            jsonObject {
                body(it)
            }
        }
    }

    inline fun <T> jsonList(list: Iterable<T>, body: (T) -> Unit) {
        beginArray()
        list.forEach {
            body(it)
        }
        endArray()
    }
}
