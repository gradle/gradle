/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.LinkedList;

// TODO:DAZ Think about a better way to do thread-safety here, maybe
public class DependencyResolutionLogger implements DependencyResolutionListener {
    private final ThreadLocal<LinkedList<ProgressLogger>> progressLoggers = new ThreadLocal<LinkedList<ProgressLogger>>();
    private final LoggerBuilder loggerBuilder;

    public DependencyResolutionLogger(ProgressLoggerFactory loggerFactory) {
        this(new LoggerBuilder(loggerFactory));
    }

    public DependencyResolutionLogger(LoggerBuilder loggerBuilder) {
        this.loggerBuilder = loggerBuilder;
    }

    //TODO SF add concurrent unit test coverage
    public void beforeResolve(ResolvableDependencies dependencies) {
        LinkedList<ProgressLogger> loggers = progressLoggers.get();
        if (loggers == null) {
            loggers = new LinkedList<ProgressLogger>();
            progressLoggers.set(loggers);
        }
        ProgressLogger logger = loggerBuilder.newLogger(dependencies);
        loggers.add(logger);
    }

    public void afterResolve(ResolvableDependencies dependencies) {
        LinkedList<ProgressLogger> loggers = progressLoggers.get();
        if (loggers == null || loggers.isEmpty()) {
            throw new IllegalStateException("Logging operation was not started or it has already completed.");
        }
        ProgressLogger logger = loggers.removeLast();
        logger.completed();
        if (loggers.isEmpty()) {
            progressLoggers.remove();
        }
    }

    static class LoggerBuilder {

        private final ProgressLoggerFactory loggerFactory;

        public LoggerBuilder(ProgressLoggerFactory loggerFactory) {
            this.loggerFactory = loggerFactory;
        }

        public ProgressLogger newLogger(ResolvableDependencies dependencies) {
            ProgressLogger logger = loggerFactory.newOperation(DependencyResolutionLogger.class);
            logger.setDescription(String.format("Resolve %s", dependencies));
            logger.setShortDescription(String.format("Resolving %s", dependencies));
            logger.started();
            return logger;
        }
    }
}
