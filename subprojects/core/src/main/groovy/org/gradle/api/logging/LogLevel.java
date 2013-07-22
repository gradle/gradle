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
package org.gradle.api.logging;

/**
 * The log levels supported by Gradle.
 */
public enum LogLevel {
    DEBUG {
        boolean isEnabled(Logger logger) {
            return logger.isDebugEnabled();
        }
        void log(Logger logger, String message) {
            logger.debug(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.debug(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.debug(message, throwable);
        }},
    INFO {
        boolean isEnabled(Logger logger) {
            return logger.isInfoEnabled();
        }
        void log(Logger logger, String message) {
            logger.info(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.info(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.info(message, throwable);
        }},
    LIFECYCLE {
        boolean isEnabled(Logger logger) {
            return logger.isLifecycleEnabled();
        }
        void log(Logger logger, String message) {
            logger.lifecycle(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.lifecycle(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.lifecycle(message, throwable);
        }},
    WARN {
        boolean isEnabled(Logger logger) {
            return logger.isWarnEnabled();
        }
        void log(Logger logger, String message) {
            logger.warn(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.warn(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.warn(message, throwable);
        }},
    QUIET {
        boolean isEnabled(Logger logger) {
            return logger.isQuietEnabled();
        }
        void log(Logger logger, String message) {
            logger.quiet(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.quiet(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.quiet(message, throwable);
        }},
    ERROR {
        boolean isEnabled(Logger logger) {
            return logger.isErrorEnabled();
        }
        void log(Logger logger, String message) {
            logger.error(message);
        }
        void log(Logger logger, String message, Object... objects) {
            logger.error(message, objects);
        }
        void log(Logger logger, String message, Throwable throwable) {
            logger.error(message, throwable);
        }};

    abstract boolean isEnabled(Logger logger);

    abstract void log(Logger logger, String message);

    abstract void log(Logger logger, String message, Object... objects);

    abstract void log(Logger logger, String message, Throwable throwable);
}
