/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging

import org.gradle.cli.CommandLineConverter
import org.gradle.logging.internal.DefaultLoggingManagerFactory
import org.gradle.logging.internal.DefaultProgressLoggerFactory
import org.gradle.logging.internal.DefaultStyledTextOutputFactory
import org.gradle.logging.internal.LoggingCommandLineConverter
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class LoggingServiceRegistryTest extends Specification {
    @Rule RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    final LoggingServiceRegistry registry = new LoggingServiceRegistry()

    def providesALoggingManagerFactory() {
        expect:
        def factory = registry.getFactory(LoggingManagerInternal.class)
        factory instanceof DefaultLoggingManagerFactory
    }

    def providesAStyledTextOutputFactory() {
        expect:
        def factory = registry.get(StyledTextOutputFactory.class)
        factory instanceof DefaultStyledTextOutputFactory
    }
    
    def providesAProgressLoggerFactory() {
        expect:
        def factory = registry.get(ProgressLoggerFactory.class)
        factory instanceof DefaultProgressLoggerFactory
    }

    def providesACommandLineConverter() {
        expect:
        def converter = registry.get(CommandLineConverter.class)
        converter instanceof LoggingCommandLineConverter
    }

    def doesNotMessWithSystemOutAndErrUntilStarted() {
        when:
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream

        when:
        loggingManager.start()

        then:
        System.out != outputs.stdOutPrintStream
        System.err != outputs.stdErrPrintStream
    }

    def canCreateANestedRegistry() {
        expect:
        def child = registry.newLogging()
        child != null
    }
    
}
