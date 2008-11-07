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
import org.slf4j.Marker;

/**
 * @author Hans Dockter
 */
public class MarkerFilter extends Filter {
    private Marker marker;

    private FilterReply onMismatch = FilterReply.NEUTRAL;

    public MarkerFilter(Marker marker) {
        this.marker = marker;
    }

    public MarkerFilter(Marker marker, FilterReply onMismatch) {
        this.marker = marker;
        this.onMismatch = onMismatch;
    }

    @Override
    public FilterReply decide(Object event) {
        LoggingEvent loggingEvent = (LoggingEvent) event;
        if (loggingEvent.getMarker() == marker && !marker.contains(Logging.DISABLED)) {
            return FilterReply.ACCEPT;
        } else {
            return onMismatch;
        }
    }

    public FilterReply getOnMismatch() {
        return onMismatch;
    }

    public void setOnMismatch(FilterReply onMismatch) {
        this.onMismatch = onMismatch;
    }

    public Marker getMarker() {
        return marker;
    }

    public void setMarker(Marker marker) {
        this.marker = marker;
    }
}
