package org.gradle.client.core.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.util.FileSize
import org.gradle.client.core.Constants.APPLICATION_NAME

// Configures logback debug logging to file
// https://logback.qos.ch/manual/configuration.html
@Suppress("MagicNumber")
class LogbackConfigurator : ContextAwareBase(), Configurator {

    override fun configure(loggerContext: LoggerContext): Configurator.ExecutionStatus {

        addInfo("Setting up $APPLICATION_NAME logging configuration.")

        val logDir = appDirs.logDirectory
        val logFilename = "application"
        val logFile = logDir.resolve("$logFilename.log")

        val appender = RollingFileAppender<ILoggingEvent>().apply {
            context = loggerContext
            name = "file-logging"
            file = logFile.toString()

            val fileAppender = this
            rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
                context = loggerContext
                fileNamePattern = "$logDir/%d{yyyy/MM}/$logFilename.gz"
                maxHistory = 30
                setTotalSizeCap(FileSize(1 * 1024 * 1024 * 1024)) // 1GB
                setParent(fileAppender)
                start()
            }

            addFilter(
                ThresholdFilter().apply {
                    context = loggerContext
                    setLevel("DEBUG")
                    start()
                }
            )

            encoder = PatternLayoutEncoder().apply {
                context = loggerContext
                pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{128} -%kvp- %msg%n"
                start()
            }

            start()
        }

        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
        rootLogger.level = Level.DEBUG
        rootLogger.addAppender(appender)

        return Configurator.ExecutionStatus.INVOKE_NEXT_IF_ANY
    }
}
