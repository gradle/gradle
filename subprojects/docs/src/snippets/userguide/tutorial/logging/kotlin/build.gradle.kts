// tag::use-slf4j[]
import org.slf4j.LoggerFactory

val slf4jLogger = LoggerFactory.getLogger("some-logger")
slf4jLogger.info("An info log message logged using SLF4j")
// end::use-slf4j[]

// tag::use-logger[]
logger.quiet("An info log message which is always logged.")
logger.error("An error log message.")
logger.warn("A warning log message.")
logger.lifecycle("A lifecycle info log message.")
logger.info("An info log message.")
logger.debug("A debug log message.")
logger.trace("A trace log message.") // Gradle never logs TRACE level logs
// end::use-logger[]

// tag::use-logger-placeholder[]
logger.info("A {} log message", "info")
// end::use-logger-placeholder[]

// tag::use-println[]
println("A message which is logged at QUIET level")
// end::use-println[]

// tag::capture-stdout[]
logging.captureStandardOutput(LogLevel.INFO)
println("A message which is logged at INFO level")
// end::capture-stdout[]

// tag::task-capture-stdout[]
tasks.register("logInfo") {
    logging.captureStandardOutput(LogLevel.INFO)
    doFirst {
        println("A task message which is logged at INFO level")
    }
}
// end::task-capture-stdout[]
