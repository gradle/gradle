/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.jsoup

import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.jsoup.Jsoup
import java.io.FilterReader
import java.io.Reader

import org.gradle.kotlin.dsl.*


internal
class JsoupFilterReader(reader: Reader) : FilterReader(DeferringReader(reader)) {

    init {
        (`in` as DeferringReader).parent = this
    }

    private
    lateinit var fileCopyDetails: FileCopyDetails

    private
    lateinit var action: Action<JsoupTransformTarget>

    internal
    fun filterReader(source: Reader): Reader {
        val document = Jsoup.parse(source.readText())
        action(JsoupTransformTarget(document, fileCopyDetails))
        return document.toString().reader()
    }
}


internal
class DeferringReader(private val source: Reader) : Reader() {

    lateinit var parent: JsoupFilterReader

    private
    var delegate: Reader? = null

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {

        if (delegate == null) {
            delegate = parent.filterReader(source)
        }

        return delegate!!.read(cbuf, off, len)
    }

    override fun close() = Unit
}
