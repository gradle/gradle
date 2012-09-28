/*
 * Copyright 2012 the original author or authors.
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

import xsbti.F0;
import xsbti.Logger;

// TODO: change mapping of log levels
public class SbtLoggerAdapter implements Logger {
    org.gradle.api.logging.Logger log;

    public SbtLoggerAdapter(org.gradle.api.logging.Logger log) {
        this.log = log;
    }

    public void error(F0<String> msg) {
        log.lifecycle(msg.apply());
    }

    public void warn(F0<String> msg) {
        log.lifecycle(msg.apply());
    }

    public void info(F0<String> msg) {
        log.lifecycle(msg.apply());
    }

    public void debug(F0<String> msg) {
        log.lifecycle(msg.apply());
    }

    public void trace(F0<Throwable> exception) {
        log.lifecycle(exception.apply().toString());
    }
}