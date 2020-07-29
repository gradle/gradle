/*
 * Copyright 2017 the original author or authors.
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

import java.io.File


internal
fun userHome() = File(System.getProperty("user.home"))


inline fun <T : AutoCloseable, U> T.useToRun(action: T.() -> U): U =
    use { run(action) }


/**
 * Appends value to the given Appendable and simple `\n` line separator after it.
 *
 * Always using the same line separator on all systems to allow for reproducible outputs.
 */
fun Appendable.appendReproducibleNewLine(value: CharSequence = ""): Appendable =
    append(value).append("\n")


internal
fun File.isParentOf(child: File): Boolean =
    child.canonicalPath.startsWith(canonicalPath)


/**
 * List files ordered by filename for reproducibility.
 * Never returns null.
 */
fun File.listFilesOrdered(filter: ((File) -> Boolean)? = null): List<File> =
    listFiles()
        ?.let { if (filter != null) it.filter(filter) else it.asList() }
        ?.sortedBy { it.name }
        ?: emptyList()
