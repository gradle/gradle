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

package org.gradle.internal.logging;

import org.apache.tools.ant.Project;
import org.gradle.api.logging.LogLevel;

import java.util.HashMap;
import java.util.Map;

public final class LogLevelMapping {

    private LogLevelMapping() {
    }

    public static final Map<Integer, LogLevel> ANT_IVY_2_SLF4J = new HashMap<Integer, LogLevel>() {
        {
            put(Project.MSG_ERR, LogLevel.ERROR);
            put(Project.MSG_WARN, LogLevel.WARN);
            put(Project.MSG_INFO, LogLevel.INFO);
            put(Project.MSG_DEBUG, LogLevel.DEBUG);
            put(Project.MSG_VERBOSE, LogLevel.DEBUG);
        }};
}
