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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.cli.CommandLineConverter
import org.gradle.logging.internal.DefaultLoggingManagerFactory
import org.gradle.logging.internal.DefaultProgressLoggerFactory
import org.gradle.logging.internal.DefaultStyledTextOutputFactory
import org.gradle.logging.internal.LoggingCommandLineConverter
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.TextUtil
import org.junit.Rule
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.logging.Logger

class LoggingServiceRegistryTest extends Specification {
    final TestOutputEventListener outputEventListener = new TestOutputEventListener()
    @Rule ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()

    def providesALoggingManagerFactory() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()

        expect:
        def factory = registry.getFactory(LoggingManagerInternal.class)
        factory instanceof DefaultLoggingManagerFactory
    }

    def providesAStyledTextOutputFactory() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()

        expect:
        def factory = registry.get(StyledTextOutputFactory.class)
        factory instanceof DefaultStyledTextOutputFactory
    }

    def providesAProgressLoggerFactory() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()

        expect:
        def factory = registry.get(ProgressLoggerFactory.class)
        factory instanceof DefaultProgressLoggerFactory
    }

    def providesACommandLineConverter() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()

        expect:
        def converter = registry.get(CommandLineConverter.class)
        converter instanceof LoggingCommandLineConverter
    }

    def resetsSlf4jWhenStarted() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = LoggerFactory.getLogger("category")

        when:
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        logger.warn("before")

        then:
        outputEventListener.toString() == '[WARN before]'

        when:
        loggingManager.level = LogLevel.INFO
        loggingManager.start()
        logger.info("ignored")
        logger.warn("warning")

        then:
        outputEventListener.toString() == '[WARN before]'
    }

    def routesSlf4jToListenersWhenStarted() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = LoggerFactory.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.addStandardOutputListener(listener)

        when:
        logger.warn("before")

        then:
        0 * listener._

        when:
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        logger.info("ignored")
        logger.warn("warning")

        then:
        1 * listener.onOutput('warning')
        1 * listener.onOutput(TextUtil.platformLineSeparator)
        0 * listener._

        when:
        loggingManager.stop()
        logger.warn("after")

        then:
        0 * listener._
    }

    def routesJavaUtilLoggingToListenersWhenStarted() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = Logger.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.addStandardOutputListener(listener)

        when:
        logger.warning("before")

        then:
        0 * listener._

        when:
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        logger.info("ignored")
        logger.warning("warning")

        then:
        1 * listener.onOutput('warning')
        1 * listener.onOutput(TextUtil.platformLineSeparator)
        0 * listener._

        when:
        loggingManager.stop()
        logger.warning("after")

        then:
        0 * listener._
    }

    def routesSystemOutAndErrToListenersWhenStarted() {
        StandardOutputListener listener = Mock()

        when:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream

        when:
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        then:
        System.out != outputs.stdOutPrintStream
        System.err != outputs.stdErrPrintStream

        when:
        System.out.println("info")
        System.err.println("error")

        then:
        1 * listener.onOutput(TextUtil.toPlatformLineSeparators("info\n"))
        1 * listener.onOutput(TextUtil.toPlatformLineSeparators("error\n"))
        0 * listener._
    }

    def routesStyledTextToListenersWhenStarted() {
        StandardOutputListener listener = Mock()

        when:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream

        when:
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        def textOutput = registry.get(StyledTextOutputFactory).create("category")
        textOutput.println("info")

        then:
        1 * listener.onOutput(TextUtil.toPlatformLineSeparators("info\n"))
        0 * listener._
    }

    def routesLoggingOutputToOriginalSystemOutAndErrWhenStarted() {
        given:
        def logger = LoggerFactory.getLogger("category")
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        when:
        logger.warn("before")
        logger.error("before")

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''

        when:
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        logger.warn("warning")
        logger.error("error")

        then:
        outputs.stdOut == TextUtil.toPlatformLineSeparators('warning\n')
        outputs.stdErr == TextUtil.toPlatformLineSeparators('error\n')
    }

    def routesSlf4jWhenEmbedded() {
        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def logger = LoggerFactory.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        when:
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        logger.warn("warning")
        logger.error("error")

        then:
        outputs.stdOut == TextUtil.toPlatformLineSeparators('warning\n')
        outputs.stdErr == TextUtil.toPlatformLineSeparators('error\n')
    }

    def doesNotMessWithJavaUtilLoggingWhenEmbedded() {
        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        def logger = Logger.getLogger("category")

        when:
        logger.warning("warning")
        logger.severe("error")

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def doesNotMessWithSystemOutputAndErrorWhenEmbedded() {
        when:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.level = LogLevel.WARN
        loggingManager.start()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def doesNotMessWithJavaUtilLoggingWhenNested() {
        given:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.level = LogLevel.WARN
        loggingManager.start()
        def logger = Logger.getLogger("category")

        when:
        logger.warning("warning")
        logger.severe("error")

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def doesNotMessWithSystemOutputAndErrorWhenNested() {
        when:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.level = LogLevel.WARN
        loggingManager.start()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def doesNotRouteToSystemOutAndErrorWhenNested() {
        StandardOutputListener listener = Mock()

        when:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        def textOutput = registry.get(StyledTextOutputFactory).create("category")
        textOutput.println("info")

        then:
        1 * listener.onOutput(TextUtil.toPlatformLineSeparators("info\n"))
        0 * listener._

        and:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }
}
