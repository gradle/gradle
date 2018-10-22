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

package org.gradle.kotlin.dsl.concurrent

import java.io.File

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import kotlin.concurrent.thread


internal
class WriterThread : AutoCloseable {

    private
    val q = ArrayBlockingQueue<Command>(64)

    private
    val failure = AtomicReference<Throwable?>(null)

    private
    val thread = thread(name = "kotlin-dsl-writer") {
        try {
            loop@ while (true) {
                when (val command = q.take()) {
                    is Command.Execute -> command.action()
                    Command.Quit -> break@loop
                }
            }
        } catch (error: Throwable) {
            failure.set(error)
        }
    }

    /**
     * Writes to the given [file] in the writer thread.
     */
    fun writeFile(file: File, bytes: ByteArray) {
        io { file.writeBytes(bytes) }
    }

    /**
     * Executes the given [action] in the writer thread.
     */
    fun io(action: () -> Unit) {
        put(Command.Execute(action))
    }

    override fun close() {
        put(Command.Quit)
        thread.join()
        checkForFailure()
    }

    private
    fun put(command: Command) {
        checkForFailure()
        q.put(command)
    }

    private
    fun checkForFailure() {
        failure.get()?.let { throw it }
    }

    private
    sealed class Command {

        class Execute(val action: () -> Unit) : Command()

        object Quit : Command()
    }
}
