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

import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.gradle.api.logging.Logging;

/**
 * @author Hans Dockter
 */
public class HighLevelFilter extends Filter {
    @Override
    public FilterReply decide(Object event) {
        LoggingEvent loggingEvent = (LoggingEvent) event;
        if (loggingEvent.getMarker() == Logging.LIFECYCLE) {
            return FilterReply.ACCEPT;
        } else {
            return FilterReply.NEUTRAL;
        }
    }
}
