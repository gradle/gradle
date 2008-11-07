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
package org.gradle.api.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.BasicMarker;
import org.apache.ivy.util.Message;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
public class Logging {
    public static final BasicMarker LIFECYCLE = (BasicMarker) MarkerFactory.getDetachedMarker("LIFECYCLE");
    public static final Marker DISABLED = MarkerFactory.getDetachedMarker("DISABLED");
    public static final Marker QUIET = MarkerFactory.getDetachedMarker("QUIET");

    public static final Map<Integer, LogLevel> ANT_IVY_2_SLF4J_LEVEL_MAPPER = new HashMap<Integer, LogLevel>() {
        {
            put(Message.MSG_ERR, LogLevel.WARN);
            put(Message.MSG_WARN, LogLevel.WARN);
            put(Message.MSG_INFO, LogLevel.INFO);
            put(Message.MSG_DEBUG, LogLevel.DEBUG);
            put(Message.MSG_VERBOSE, LogLevel.DEBUG);
        }
    };
}
