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

package org.gradle.kotlin.dsl.provider.capture

/** A `kotlin.jvm.internal.Ref$XxxRef` mutable cell holding a captured field's value in its `element`. */
internal data class Cell(val internalName: String, val elementDesc: String, val isObject: Boolean) {
    val descriptor: String get() = "L$internalName;"
}

/** The `Ref` cell type that backs a field of the given descriptor (a primitive `Ref$XxxRef`, else `Ref$ObjectRef`). */
internal fun cellFor(fieldDesc: String): Cell = when (fieldDesc) {
    "I" -> Cell("kotlin/jvm/internal/Ref\$IntRef", "I", false)
    "J" -> Cell("kotlin/jvm/internal/Ref\$LongRef", "J", false)
    "Z" -> Cell("kotlin/jvm/internal/Ref\$BooleanRef", "Z", false)
    "B" -> Cell("kotlin/jvm/internal/Ref\$ByteRef", "B", false)
    "C" -> Cell("kotlin/jvm/internal/Ref\$CharRef", "C", false)
    "S" -> Cell("kotlin/jvm/internal/Ref\$ShortRef", "S", false)
    "F" -> Cell("kotlin/jvm/internal/Ref\$FloatRef", "F", false)
    "D" -> Cell("kotlin/jvm/internal/Ref\$DoubleRef", "D", false)
    else -> Cell("kotlin/jvm/internal/Ref\$ObjectRef", "Ljava/lang/Object;", true)
}

/** The cell descriptor for a captured item — the shared-cell type is always what a lambda carries. */
internal fun ScriptModel.cellDescriptorFor(item: String): String = cellFor(fieldDesc(item)).descriptor
