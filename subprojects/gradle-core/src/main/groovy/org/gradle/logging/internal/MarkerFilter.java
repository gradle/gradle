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
package org.gradle.logging.internal;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

import java.util.Arrays;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class MarkerFilter extends Filter<ILoggingEvent> {
    private final List<Marker> markers;

    private FilterReply onMismatch = FilterReply.NEUTRAL;

    public MarkerFilter(Marker... markers) {
        this.markers = Arrays.asList(markers);
    }

    public MarkerFilter(FilterReply onMismatch, Marker... markers) {
        this(markers);
        this.onMismatch = onMismatch;
    }

    @Override
    public FilterReply decide(ILoggingEvent loggingEvent) {
        Marker marker = loggingEvent.getMarker();
        if (marker != null) {
            for (Marker candidate : markers) {
                if (marker.contains(candidate)) {
                    return FilterReply.ACCEPT;
                }
            }
        }
        return onMismatch;
    }

    public FilterReply getOnMismatch() {
        return onMismatch;
    }

    public void setOnMismatch(FilterReply onMismatch) {
        this.onMismatch = onMismatch;
    }

    public List getMarkers() {
        return markers;
    }
}
