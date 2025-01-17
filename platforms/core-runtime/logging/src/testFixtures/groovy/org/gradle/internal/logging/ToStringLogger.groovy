/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.logging

import groovy.transform.CompileStatic
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLogger
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext
import org.gradle.internal.time.MockClock
import org.slf4j.helpers.FormattingTuple
import org.slf4j.helpers.MessageFormatter

@CompileStatic
class ToStringLogger extends OutputEventListenerBackedLogger {
    private final StringBuilder log = new StringBuilder()

    ToStringLogger() {
        super("ToStringLogger", new OutputEventListenerBackedLoggerContext(new MockClock()), new MockClock())
    }

    @Override
    void lifecycle(String message) {
        log(message)
    }

    @Override
    void lifecycle(String format, Object... args) {
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, args)
        log(tuple.getMessage())
    }

    @Override
    void debug(String message) {
        log(message)
    }

    @Override
    void debug(String format, Object args) {
        FormattingTuple tuple = MessageFormatter.format(format, args)
        log(tuple.getMessage())
    }

    @Override
    void debug(String format, Object... args) {
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, args)
        log(tuple.getMessage())
    }

    @Override
    void warn(String message) {
        log(message)
    }

    @Override
    void warn(String format, Object args) {
        FormattingTuple tuple = MessageFormatter.format(format, args)
        log(tuple.getMessage())
    }

    @Override
    void warn(String format, Object... args) {
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, args)
        log(tuple.getMessage())
    }

    private void log(String message) {
        log.append(message)
        log.append('\n')
    }

    @Override
    String toString() {
        log.toString()
    }
}
