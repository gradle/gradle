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

package org.gradle.internal.logging.services

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.TestOutputEventListener
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.util.internal.RedirectStdOutAndErr
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.logging.Level
import java.util.logging.Logger

class LoggingServiceRegistryTest extends Specification {
    final TestOutputEventListener outputEventListener = new TestOutputEventListener()
    @Rule
    ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule
    RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()

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

    def resetsSlf4jWhenStarted() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = LoggerFactory.getLogger("category")

        when:
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        logger.warn("before")

        then:
        outputEventListener.toString() == '[[WARN] [category] before]'

        when:
        loggingManager.levelInternal = LogLevel.INFO
        loggingManager.start()
        logger.info("ignored")
        logger.warn("warning")

        then:
        outputEventListener.toString() == '[[WARN] [category] before]'
    }

    def consumesSlf4jWhenStarted() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = LoggerFactory.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)

        when:
        logger.warn("before")

        then:
        0 * listener._

        when:
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()
        logger.info("ignored")
        logger.warn("warning")

        then:
        1 * listener.onOutput('warning')
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        loggingManager.stop()
        logger.warn("after")

        then:
        0 * listener._
    }

    def consumesFromJavaUtilLoggingWhenStarted() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def logger = Logger.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)

        when:
        logger.warning("before")

        then:
        0 * listener._

        when:
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()
        logger.info("ignored")
        logger.warning("warning")

        then:
        1 * listener.onOutput('warning')
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        loggingManager.stop()
        logger.warning("after")

        then:
        0 * listener._
    }

    def configuresJavaUtilLoggingAndRestoresSettingsWhenStopped() {
        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def rootLogger = Logger.getLogger("")
        def logger = Logger.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        when:
        rootLogger.level = Level.OFF
        loggingManager.levelInternal = LogLevel.WARN

        then:
        rootLogger.level == Level.OFF
        logger.level == null

        when:
        loggingManager.start()

        then:
        rootLogger.level != Level.OFF
        logger.level == null

        when:
        loggingManager.stop()

        then:
        rootLogger.level == Level.OFF
        logger.level == null
    }

    def consumesFromSystemOutAndErrWhenStarted() {
        StandardOutputListener listener = Mock()

        when:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream

        when:
        loggingManager.enableUserStandardOutputListeners()
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
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)

        then:
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        loggingManager.stop()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def canChangeAndRestoreSystemOutputAndErrorCaptureLevels() {
        def listener = Mock(StandardOutputListener)

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.captureStandardError(LogLevel.WARN)
        loggingManager.captureStandardOutput(LogLevel.INFO)
        loggingManager.levelInternal = LogLevel.INFO
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        when:
        def nestedManager = registry.newInstance(LoggingManagerInternal)
        nestedManager.captureStandardError(LogLevel.INFO)
        nestedManager.captureStandardOutput(LogLevel.DEBUG)
        nestedManager.start()

        System.out.println("info")
        System.err.println("error")

        then:
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        nestedManager.stop()

        System.out.println("info")
        System.err.println("error")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._
    }

    def buffersLinesWrittenToSystemOutAndErr() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        when:
        System.out.print("in")
        System.err.print("err")

        then:
        0 * listener._

        when:
        System.out.println("fo")
        System.err.print("or")
        System.err.println()

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)

        then:
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        System.out.print("buffered")
        System.err.print("error")
        System.err.flush()

        then:
        1 * listener.onOutput("error")
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
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        def textOutput = registry.get(StyledTextOutputFactory).create("category")
        textOutput.println("info")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._
    }

    def buffersTextWrittenToStyledText() {
        StandardOutputListener listener = Mock()

        given:
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        when:
        def textOutput = registry.get(StyledTextOutputFactory).create("category")
        textOutput.text("in")

        then:
        0 * listener._

        when:
        textOutput.println("fo")
        textOutput.text("buffered")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
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
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()
        logger.warn("warning")
        logger.error("error")

        then:
        outputs.stdOut == TextUtil.toPlatformLineSeparators('warning\n')
        outputs.stdErr == TextUtil.toPlatformLineSeparators('error\n')
    }

    def consumesSlf4jWhenEmbedded() {
        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def logger = LoggerFactory.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        def listener = Mock(StandardOutputListener)

        when:
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()
        logger.warn("warning")
        logger.error("error")

        then:
        1 * listener.onOutput("warning")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._
    }

    def doesNotRouteToSystemOutAndErrorWhenEmbedded() {
        def listener = Mock(StandardOutputListener)

        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.setLevelInternal(LogLevel.INFO)
        loggingManager.start()

        when:
        def logger = LoggerFactory.getLogger("category")
        logger.info("info")
        logger.error("error")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        and:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def doesNotConsumeJavaUtilLoggingWhenEmbedded() {
        def listener = Mock(StandardOutputListener)

        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()
        def logger = Logger.getLogger("category")

        when:
        logger.info("info")
        logger.warning("warning")
        logger.severe("error")

        then:
        0 * listener._

        and:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def canEnableConsumingJavaUtilLoggingWhenEmbedded() {
        def listener = Mock(StandardOutputListener)

        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def logger = Logger.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()

        when:
        logger.severe("before")
        logger.warning("before")
        logger.info("before")

        then:
        0 * listener._

        when:
        loggingManager.captureSystemSources()
        logger.info("ignored")
        logger.warning("warning")
        logger.severe("error")

        then:
        1 * listener.onOutput('warning')
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        1 * listener.onOutput('error')
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        loggingManager.stop()
        logger.severe("after")
        logger.warning("after")
        logger.info("after")

        then:
        0 * listener._
    }

    def restoresJavaUtilLoggingSettingsWhenEmbeddedAndStopped() {
        given:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def rootLogger = Logger.getLogger("")
        def logger = Logger.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)

        when:
        rootLogger.level = Level.OFF
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.captureSystemSources()

        then:
        rootLogger.level == Level.OFF
        logger.level == null

        when:
        loggingManager.start()

        then:
        rootLogger.level != Level.OFF
        logger.level == null

        when:
        loggingManager.stop()

        then:
        rootLogger.level == Level.OFF
        logger.level == null
    }

    def doesNotConsumeFromSystemOutAndErrWhenEmbedded() {
        when:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.levelInternal = LogLevel.INFO
        loggingManager.start()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def canEnabledConsumingFromSystemOutAndErrWhenEmbedded() {
        StandardOutputListener listener = Mock()

        when:
        def registry = LoggingServiceRegistry.newEmbeddableLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream

        when:
        loggingManager.captureSystemSources()

        then:
        System.out != outputs.stdOutPrintStream
        System.err != outputs.stdErrPrintStream

        when:
        System.out.println("info")
        System.err.println("error")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)

        then:
        1 * listener.onOutput("error")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        when:
        loggingManager.stop()

        then:
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def doesNotConsumeSlf4jWhenNested() {
        given:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def logger = LoggerFactory.getLogger("category")
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        def listener = Mock(StandardOutputListener)

        when:
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.addStandardErrorListener(listener)
        loggingManager.start()
        logger.warn("warning")
        logger.error("error")

        then:
        0 * listener._
    }

    def doesNotConsumeJavaUtilLoggingWhenNested() {
        given:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.levelInternal = LogLevel.WARN
        loggingManager.start()
        def logger = Logger.getLogger("category")

        when:
        logger.warning("warning")
        logger.severe("error")

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def doesNotConsumeFromSystemOutputAndErrorWhenNested() {
        when:
        def registry = LoggingServiceRegistry.newNestedLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.levelInternal = LogLevel.WARN
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
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        def textOutput = registry.get(StyledTextOutputFactory).create("category")
        textOutput.println("info")

        then:
        1 * listener.onOutput("info")
        1 * listener.onOutput(SystemProperties.instance.lineSeparator)
        0 * listener._

        and:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }
}
