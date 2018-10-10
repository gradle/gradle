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

package org.gradle.kotlin.dsl.accessors

import java.io.File

import java.util.concurrent.ArrayBlockingQueue

import kotlin.concurrent.thread


internal
class WriterThread : AutoCloseable {

    private
    val q = ArrayBlockingQueue<Command>(64)

    private
    val thread = thread(name = "kotlin-dsl-writer") {
        loop@ while (true) {
            when (val command = q.take()) {
                is Command.WriteFile -> command.run {
                    file.parentFile.mkdirs()
                    file.writeBytes(bytes)
                }
                Command.Done -> break@loop
            }
        }
    }

    fun writeFile(file: File, bytes: ByteArray) {
        q.put(Command.WriteFile(file, bytes))
    }

    override fun close() {
        q.put(Command.Done)
        thread.join()
    }

    private
    sealed class Command {

        class WriteFile(val file: File, val bytes: ByteArray) : Command()

        object Done : Command()
    }
}
