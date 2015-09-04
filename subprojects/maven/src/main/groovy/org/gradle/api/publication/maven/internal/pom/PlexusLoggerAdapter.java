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
package org.gradle.api.publication.maven.internal.pom;

import org.codehaus.plexus.logging.Logger;

public class PlexusLoggerAdapter implements Logger {
    org.slf4j.Logger logger;

    public PlexusLoggerAdapter(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    public void debug(String s) {
        logger.debug(s);
    }

    public void debug(String s, Throwable throwable) {
        logger.debug(s, throwable);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void info(String s) {
        logger.info(s);
    }

    public void info(String s, Throwable throwable) {
        logger.info(s, throwable);
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public void warn(String s) {
        logger.warn(s);
    }

    public void warn(String s, Throwable throwable) {
        logger.warn(s, throwable);
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public void error(String s) {
        logger.error(s);
    }

    public void error(String s, Throwable throwable) {
        logger.error(s, throwable);
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public void fatalError(String s) {
        logger.error(s);
    }

    public void fatalError(String s, Throwable throwable) {
        logger.error(s, throwable);
    }

    public boolean isFatalErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public Logger getChildLogger(String s) {
        throw new UnsupportedOperationException();
    }

    public int getThreshold() {
        throw new UnsupportedOperationException();
    }

    public void setThreshold(int arg0) {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return logger.getName();
    }
}
