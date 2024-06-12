/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.language


interface SourceData {
    val sourceIdentifier: SourceIdentifier
    val indexRange: IntRange
    val lineRange: IntRange
    val startColumn: Int
    val endColumn: Int

    fun text(): String

    companion object {
        val NONE = object : SourceData {
            override val sourceIdentifier: SourceIdentifier
                get() = SourceIdentifier("<none>")
            override val indexRange: IntRange
                get() = IntRange.EMPTY
            override val lineRange: IntRange
                get() = IntRange.EMPTY
            override val startColumn: Int
                get() = -1
            override val endColumn: Int
                get() = -1

            override fun text(): String = "<none>"
        }
    }
}


object SyntheticallyProduced : SourceData {
    override val sourceIdentifier: SourceIdentifier = SourceIdentifier("<synthetic>")
    override val indexRange: IntRange = IntRange.EMPTY
    override val lineRange: IntRange = IntRange.EMPTY
    override val startColumn: Int = 0
    override val endColumn: Int = 0

    override fun text(): String = ""
}


data class SourceIdentifier(val fileIdentifier: String)
