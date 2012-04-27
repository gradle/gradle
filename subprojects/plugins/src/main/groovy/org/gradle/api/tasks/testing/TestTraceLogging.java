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

package org.gradle.api.tasks.testing;

import org.gradle.util.GUtil;

import java.util.*;

public class TestTraceLogging {
    private boolean enabled;
    private Set<TraceEvent> events = EnumSet.allOf(TraceEvent.class);
    private int minDetailLevel = 2;
    private int maxDetailLevel = Integer.MAX_VALUE;
    private PackageFormat packageFormat = PackageFormat.SHORT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean flag) {
        this.enabled = flag;
    }

    public void enabled(boolean flag) {
        this.enabled = flag;
    }

    public Set<TraceEvent> getEvents() {
        return events;
    }

    public void setEvents(Iterable<Object> events) {
        this.events = EnumSet.noneOf(TraceEvent.class);
        for (Object event : events) {
            this.events.add(convertEvent(event));
        }
    }

    public void events(Object... events) {
        for (Object event : events) {
            this.events.add(convertEvent(event));
        }
    }

    private TraceEvent convertEvent(Object event) {
        if (event instanceof TraceEvent) {
            return (TraceEvent) event;
        }
        return TraceEvent.valueOf(GUtil.toConstant(event.toString()));
    }

    public int getMinDetailLevel() {
        return minDetailLevel;
    }

    public void setMinDetailLevel(int level) {
        minDetailLevel = level;
    }

    public void minDetailLevel(int level) {
        minDetailLevel = level;
    }

    public int getMaxDetailLevel() {
        return maxDetailLevel;
    }

    public void setMaxDetailLevel(int level) {
        maxDetailLevel = level;
    }

    public void maxDetailLevel(int level) {
        maxDetailLevel = level;
    }

    public PackageFormat getPackageFormat() {
        return packageFormat;
    }

    public void setPackageFormat(Object packageFormat) {
        this.packageFormat = convertPackageFormat(packageFormat);
    }

    public void packageFormat(Object packageFormat) {
        this.packageFormat = convertPackageFormat(packageFormat);
    }

    private PackageFormat convertPackageFormat(Object packageFormat) {
        return PackageFormat.valueOf(GUtil.toConstant(packageFormat.toString()));
    }
}
