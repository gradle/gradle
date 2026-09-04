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

package org.gradle.kotlin.dsl.support

import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.FileBasedScriptSource


/**
 * [SourceCode.text] on file-based sources opens a stream it never closes (KT-88453),
 * holding a file handle in the compiler process until GC; read the file directly instead.
 */
internal
fun textOf(script: SourceCode): String =
    when (script) {
        is FileBasedScriptSource -> script.file.readText().removePrefix("\uFEFF")
        else -> script.text
    }
