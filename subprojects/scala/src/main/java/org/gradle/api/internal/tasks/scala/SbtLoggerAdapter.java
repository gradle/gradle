/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.function.Supplier;

class SbtLoggerAdapter implements xsbti.Logger {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);

    @Override
    public void error(Supplier<String> msg) {
        LOGGER.error(msg.get());
    }

    @Override
    public void warn(Supplier<String> msg) {
        LOGGER.warn(msg.get());
    }

    @Override
    public void info(Supplier<String> msg) {
        LOGGER.info(msg.get());
    }

    @Override
    public void debug(Supplier<String> msg) {
        LOGGER.debug(msg.get());
    }

    @Override
    public void trace(Supplier<Throwable> exception) {
        LOGGER.trace(exception.get().toString());
    }
}