/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.codegen


typealias KDocProvider = (String) -> KDoc?


data class KDoc(val text: String) {

    val parameterNames: List<String> by lazy {
        kDocParamRegex
            .findAll(text)
            .map { it.groups[1]!!.value }
            .toList()
    }

    fun format(notice: String? = null): String =
        StringBuilder().run {
            appendln("/**")

            var noticeToInsert = notice
            text.lineSequence().forEach {
                if (noticeToInsert != null && it.startsWith('@')) {
                    appendln(" * $noticeToInsert")
                    appendln(" *")
                    noticeToInsert = null
                }
                appendln(" * $it".trimEnd())
            }

            appendln(" */")
            toString()
        }
}


private
val kDocParamRegex = Regex("^@param\\s+(\\w+)", RegexOption.MULTILINE)


object MarkdownKDocProvider {

    fun from(markdown: String): KDocProvider =
        { signature -> findKDocIn(markdown, signature) }

    fun fromResource(resourcePath: String) =
        from(javaClass.getResource(resourcePath).readText())

    private fun findKDocIn(markdown: String, signature: String): KDoc? {
        val signatureStart = markdown.indexOf("# $signature")
        return if (signatureStart >= 0) {
            val startIndex = markdown.indexOf("\n", signatureStart)
            val endIndex = markdown.indexOf("\n#", startIndex + 1)
            val text =
                if (endIndex < 0) markdown.substring(startIndex)
                else markdown.substring(startIndex, endIndex)
            KDoc(text.trim())
        } else
            null
    }
}
