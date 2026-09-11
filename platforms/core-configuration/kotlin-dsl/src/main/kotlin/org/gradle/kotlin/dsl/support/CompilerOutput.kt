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

package org.gradle.kotlin.dsl.support

import org.gradle.internal.io.NullOutputStream
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream


internal object CompilerOutput {

    inline fun <T> withRedirecting(logger: Logger, action: () -> T): T {
        return when {
            logger.isDebugEnabled -> {
                loggingOutputTo(logger::debug) { action() }
            }

            else -> {
                ignoringOutputOf { action() }
            }
        }
    }


    private
    inline fun <T> loggingOutputTo(noinline log: (String) -> Unit, action: () -> T): T =
        redirectingOutputTo({ LoggingOutputStream(log) }, action)


    private
    inline fun <T> ignoringOutputOf(action: () -> T): T =
        redirectingOutputTo({ NullOutputStream.INSTANCE }, action)


    private
    inline fun <T> redirectingOutputTo(noinline outputStream: () -> OutputStream, action: () -> T): T =
        redirecting(System.err, System::setErr, outputStream()) {
            redirecting(System.out, System::setOut, outputStream()) {
                action()
            }
        }


    private
    inline fun <T> redirecting(
        stream: PrintStream,
        set: (PrintStream) -> Unit,
        to: OutputStream,
        action: () -> T
    ): T = try {
        set(PrintStream(to, true))
        action()
    } finally {
        set(stream)
        to.flush()
    }


    private
    class LoggingOutputStream(val log: (String) -> Unit) : OutputStream() {

        private
        val buffer = ByteArrayOutputStream()

        override fun write(b: Int) = buffer.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)

        override fun flush() {
            buffer.run {
                val string = toString("utf8")
                if (string.isNotBlank()) {
                    log(string)
                }
                reset()
            }
        }

        override fun close() {
            flush()
        }
    }
}
