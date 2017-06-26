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

package org.gradle.build.docs

class SnippetWriter {

    private final File dest
    private final String displayName
    private boolean appendToFile
    private PrintWriter writer

    def SnippetWriter(String displayName, File dest) {
        this.dest = dest
        this.displayName = displayName
    }

    def start() {
        if (writer) {
            throw new RuntimeException("$displayName is already started.")
        }
        dest.parentFile.mkdirs()
        writer = new PrintWriter(dest.newWriter(appendToFile), false)
        appendToFile = true
        this
    }

    def println(String line) {
        writer?.println(line)
    }

    def end() {
        if (!writer) {
            throw new RuntimeException("$displayName was not started.")
        }
        close()
    }

    def close() {
        writer?.close()
        writer = null
    }
}
