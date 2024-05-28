/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.graph

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger


class LoggingTracer(
    private val profile: String,
    private val writePosition: () -> Long,
    private val logger: Logger,
    private val level: LogLevel
) : Tracer {

    // Include a sequence number in the events so the order of events can be preserved in face of log output reordering
    private
    var nextSequenceNumber = 0L

    override fun open(frame: String) {
        log(frame, 'O')
    }

    override fun close(frame: String) {
        log(frame, 'C')
    }

    private
    fun log(frame: String, openOrClose: Char) {
        logger.log(
            level,
            """{"profile":"$profile","type":"$openOrClose","frame":"$frame","at":${writePosition()},"sn":${nextSequenceNumber()}}"""
        )
    }

    private
    fun nextSequenceNumber() = nextSequenceNumber.also {
        nextSequenceNumber += 1
    }
}
