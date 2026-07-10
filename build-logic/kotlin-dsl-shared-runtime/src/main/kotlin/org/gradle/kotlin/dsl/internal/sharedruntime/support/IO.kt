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

package org.gradle.kotlin.dsl.internal.sharedruntime.support


/**
 * Appends value to the given Appendable and simple `\n` line separator after it.
 *
 * Always using the same line separator on all systems to allow for reproducible outputs.
 */
fun Appendable.appendReproducibleNewLine(value: CharSequence = ""): Appendable {
    assert('\r' !in value) {
        "Unexpected line ending in string."
    }
    return append(value).append("\n")
}
