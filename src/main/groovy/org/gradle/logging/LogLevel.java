/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.logging;

import org.slf4j.Marker;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Hans Dockter
 */
public enum LogLevel {
    DEBUG {
        public void log(Marker marker, String message) {
            logger.debug(marker, message);
        }
        public void log(Marker marker, String message, Throwable t) {
            logger.debug(marker, message, t);
        }},
    INFO {
        public void log(Marker marker, String message) {
            logger.info(marker, message);
        }
        public void log(Marker marker, String message, Throwable t) {
            logger.debug(marker, message, t);
        }},
    WARN {
        public void log(Marker marker, String message) {
            logger.warn(marker, message);
        }
        public void log(Marker marker, String message, Throwable t) {
            logger.debug(marker, message, t);
        }},
    ERROR {
        public void log(Marker marker, String message) {
            logger.error(marker, message);
        }
        public void log(Marker marker, String message, Throwable t) {
            logger.debug(marker, message, t);
        }};

    private static Logger logger = LoggerFactory.getLogger(LogLevel.class);

    public abstract void log(Marker marker, String message);

    public abstract void log(Marker marker, String message, Throwable t);

}
