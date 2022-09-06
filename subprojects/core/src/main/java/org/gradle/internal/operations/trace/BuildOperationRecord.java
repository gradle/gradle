/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.trace;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.transform;

public final class BuildOperationRecord {

    public static final Ordering<BuildOperationRecord> ORDERING = Ordering.natural()
        .onResultOf((Function<BuildOperationRecord, Comparable<Long>>) input -> input.startTime)
        .compound(Ordering.natural().onResultOf(input -> input.id));

    public final Long id;
    public final Long parentId;
    public final String displayName;
    public final long startTime;
    public final long endTime;
    public final Map<String, ?> details;
    private final String detailsClassName;
    public final Map<String, ?> result;
    private final String resultClassName;
    public final String failure;

    public final List<Progress> progress;
    public final List<BuildOperationRecord> children;

    BuildOperationRecord(
        Long id,
        Long parentId,
        String displayName,
        long startTime,
        long endTime,
        Map<String, ?> details,
        String detailsClassName,
        Map<String, ?> result,
        String resultClassName,
        String failure,
        List<Progress> progress,
        List<BuildOperationRecord> children
    ) {
        this.id = id;
        this.parentId = parentId;
        this.displayName = displayName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.details = details == null ? null : new StrictMap<String, Object>(details);
        this.detailsClassName = detailsClassName;
        this.result = result == null ? null : new StrictMap<String, Object>(result);
        this.resultClassName = resultClassName;
        this.failure = failure;
        this.progress = progress;
        this.children = children;
    }

    Map<String, ?> toSerializable() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("displayName", displayName);

        map.put("id", id);

        if (parentId != null) {
            map.put("parentId", parentId);
        }

        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("duration", endTime - startTime);

        if (details != null) {
            map.put("details", details);
            map.put("detailsClassName", detailsClassName);
        }

        if (result != null) {
            map.put("result", result);
            map.put("resultClassName", resultClassName);
        }

        if (failure != null) {
            map.put("failure", failure);
        }

        if (!progress.isEmpty()) {
            map.put("progress", transform(progress, Progress::toSerializable));
        }

        if (!children.isEmpty()) {
            map.put("children", transform(children, BuildOperationRecord::toSerializable));
        }

        return map;
    }

    List<Map<String, ?>> toSerializableTraceEvents() {
        ImmutableList.Builder<Map<String, ?>> events = new ImmutableList.Builder<>();
        toSerializableTraceEvents(events);
        return events.build();
    }

    private void toSerializableTraceEvents(ImmutableList.Builder<Map<String, ?>> events) {
        Map<String, Object> beginEvent = new LinkedHashMap<>();
        beginEvent.put("name", displayName);
        beginEvent.put("cat", "");
        beginEvent.put("ph", "B");
        beginEvent.put("pid", 1);
        beginEvent.put("tid", 1);
        beginEvent.put("ts", startTime * 1000); // micro-second timestamp

        if (details != null) {
            Map<String, Object> beginEventArgs = new LinkedHashMap<>();
            beginEventArgs.put("opid", id);
            beginEventArgs.put("details", details);
            beginEventArgs.put("detailsClassName", detailsClassName);

            beginEvent.put("args", beginEventArgs);
        }

        events.add(beginEvent);

        for (Progress p : progress) {
            events.add(p.toSerializableTraceEvent());
        }

        for (BuildOperationRecord child : children) {
            child.toSerializableTraceEvents(events);
        }

        Map<String, Object> endEvent = new LinkedHashMap<>();
        endEvent.put("ph", "E");
        endEvent.put("pid", 1);
        endEvent.put("tid", 1);
        endEvent.put("ts", endTime * 1000); // micro-second timestamp
        if (result != null) {
            Map<String, Object> endEventArgs = new LinkedHashMap<>();
            endEventArgs.put("resultClassName", resultClassName);
            endEventArgs.put("result", result);
            if (failure != null) {
                endEventArgs.put("failure", failure);
            }

            endEvent.put("args", endEventArgs);
        }

        events.add(endEvent);
    }

    public boolean hasDetailsOfType(Class<?> clazz) throws ClassNotFoundException {
        Class<?> detailsType = getDetailsType();
        return detailsType != null && clazz.isAssignableFrom(detailsType);
    }

    public Class<?> getDetailsType() throws ClassNotFoundException {
        return detailsClassName == null ? null : getClass().getClassLoader().loadClass(detailsClassName);
    }

    public Class<?> getResultType() throws ClassNotFoundException {
        return resultClassName == null ? null : getClass().getClassLoader().loadClass(resultClassName);
    }

    @Override
    public String toString() {
        return "BuildOperationRecord{" + id + "->" + displayName + '}';
    }

    public static class Progress {
        public final long time;
        public final Map<String, ?> details;
        public final String detailsClassName;

        public Progress(
            long time,
            Map<String, ?> details,
            String detailsClassName
        ) {
            this.time = time;
            this.details = details == null ? null : new StrictMap<String, Object>(details);
            this.detailsClassName = detailsClassName;
        }

        Map<String, ?> toSerializable() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", time);

            if (details != null) {
                map.put("details", details);
                map.put("detailsClassName", detailsClassName);
            }

            return map;
        }

        Map<String, ?> toSerializableTraceEvent() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ph", "i");
            map.put("pid", 1);
            map.put("tid", 1);
            map.put("ts", time * 1000); // micro-second timestamp

            if (details != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("details", details);
                args.put("detailsClassName", detailsClassName);

                map.put("args", args);
            }

            return map;
        }

        public Class<?> getDetailsType() throws ClassNotFoundException {
            return detailsClassName == null ? null : getClass().getClassLoader().loadClass(detailsClassName);
        }

        public boolean hasDetailsOfType(Class<?> clazz) throws ClassNotFoundException {
            Class<?> detailsType = getDetailsType();
            return detailsType != null && clazz.isAssignableFrom(detailsType);
        }

        @Override
        public String toString() {
            return "Progress{details=" + details + ", detailsClassName='" + detailsClassName + '\'' + '}';
        }
    }
}
