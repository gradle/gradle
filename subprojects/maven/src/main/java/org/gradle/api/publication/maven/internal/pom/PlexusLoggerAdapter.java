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

    @Override
    public void debug(String s) {
        logger.debug(s);
    }

    @Override
    public void debug(String s, Throwable throwable) {
        logger.debug(s, throwable);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void info(String s) {
        logger.info(s);
    }

    @Override
    public void info(String s, Throwable throwable) {
        logger.info(s, throwable);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void warn(String s) {
        logger.warn(s);
    }

    @Override
    public void warn(String s, Throwable throwable) {
        logger.warn(s, throwable);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void error(String s) {
        logger.error(s);
    }

    @Override
    public void error(String s, Throwable throwable) {
        logger.error(s, throwable);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void fatalError(String s) {
        logger.error(s);
    }

    @Override
    public void fatalError(String s, Throwable throwable) {
        logger.error(s, throwable);
    }

    @Override
    public boolean isFatalErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public Logger getChildLogger(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getThreshold() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setThreshold(int arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return logger.getName();
    }
}
