/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationCache
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING


/**
 * Materialises [scriptText] at a stable per-[scriptIdentity] path under the kotlin-dsl IC cache
 * and invokes [action] with that file. The path persists across builds, which (a) lets BTA's
 * incremental compilation see the same source file across iterations and (b) avoids per-compile
 * temp-dir churn.
 *
 * The text is written atomically (temp + rename) so concurrent compiles of the same identity
 * sharing `GRADLE_USER_HOME` cannot expose a partially-written file. The content is fully
 * determined by `(scriptIdentity, scriptText)`, so racing writers produce identical bytes.
 *
 * [scriptIdentity] must match the identity used downstream as BTA's IC key (see
 * `compileScript`'s `"$originalPath#$stage"`) so the source path and the IC working state agree.
 *
 * The file is not deleted after [action] returns: that's the point — the next build's call with
 * the same identity finds the same path, which is what BTA's source-snapshot needs to compute a
 * real diff instead of treating every iteration as a brand-new source.
 */
internal
inline fun <T> KotlinDslIncrementalCompilationCache.withStableScriptFileFor(
    scriptIdentity: String,
    scriptPath: String,
    scriptText: String,
    action: (File) -> T
): T {
    val target = scriptSourceFile(scriptIdentity, scriptFileNameFor(scriptPath))
    val tmp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
        Files.write(tmp, scriptText.toByteArray(StandardCharsets.UTF_8))
        Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(tmp)
    }
    return action(target.toFile())
}


private
fun scriptFileNameFor(scriptPath: String) = scriptPath.run {
    val index = lastIndexOf('/')
    if (index != -1) substring(index + 1, length)
    else substringAfterLast('\\')
}
