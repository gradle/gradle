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

package org.gradle.instantexecution.serialization

import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


class WriteStrings {

    fun writeString(string: CharSequence, encoder: Encoder) = encoder.run {
        string.toString().let { string ->
            val id = strings[string]
            if (id != null) {
                writeSmallInt(id)
            } else {
                val newId = strings.size
                writeSmallInt(-1)
                writeString(string)
                strings[string] = newId
            }
        }
    }

    private
    val strings = HashMap<String, Int>()
}


class ReadStrings {

    fun readString(decoder: Decoder): String = decoder.run {
        when (val index = readSmallInt()) {
            -1 -> readString().also { strings.add(it) }
            else -> strings[index]
        }
    }

    private
    val strings = mutableListOf<String>()
}
