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

package org.gradle.kotlin.dsl.execution

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import java.io.File


internal
inline fun <T> TemporaryFileProvider.withTemporaryScriptFileFor(scriptPath: String, scriptText: String, action: (File) -> T): T =
    createTemporaryDirectory("gradle-kotlin-dsl-", null).let { tempDir ->
        try {
            val tempFile = canonicalScriptFileFor(tempDir, scriptPath, scriptText)
            try {
                action(tempFile)
            } finally {
                tempFile.delete()
            }
        } finally {
            tempDir.delete()
        }
    }


private
fun canonicalScriptFileFor(baseDir: File, scriptPath: String, scriptText: String): File =
    baseDir.resolve(scriptFileNameFor(scriptPath)).run {
        writeText(scriptText)
        canonicalFile
    }


private
fun scriptFileNameFor(scriptPath: String) = scriptPath.run {
    val index = lastIndexOf('/')
    if (index != -1) substring(index + 1, length) else substringAfterLast('\\')
}
