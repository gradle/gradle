/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.intellij.lang.annotations.Language

object ParseTestUtil {

    fun parse(@Language("dcl") code: String): LanguageTreeResult {
        val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
        return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test"))
    }

    fun parseAsTopLevelBlock(@Language("dcl") code: String): Block {
        val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
        return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test")).topLevelBlock
    }

}
