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
import com.google.common.collect.Ordering;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
        @Nullable Long parentId,
        String displayName,
        long startTime,
        long endTime,
        @Nullable Map<String, ?> details,
        @Nullable String detailsClassName,
        @Nullable Map<String, ?> result,
        @Nullable String resultClassName,
        @Nullable String failure,
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

    public boolean hasDetailsOfType(Class<?> clazz) throws ClassNotFoundException {
        Class<?> detailsType = getDetailsType();
        return detailsType != null && clazz.isAssignableFrom(detailsType);
    }

    public @Nullable Class<?> getDetailsType() throws ClassNotFoundException {
        return detailsClassName == null ? null : getClass().getClassLoader().loadClass(detailsClassName);
    }

    public @Nullable Class<?> getResultType() throws ClassNotFoundException {
        return resultClassName == null ? null : getClass().getClassLoader().loadClass(resultClassName);
    }

    /**
     * Returns all progress events with details of the given type.
     */
    public List<Progress> progress(Class<?> clazz) throws ClassNotFoundException {
        List<Progress> result = new ArrayList<>();
        for (Progress progress : progress) {
            if (progress.hasDetailsOfType(clazz)) {
                result.add(progress);
            }
        }
        return result;
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
            @Nullable Map<String, ?> details,
            @Nullable String detailsClassName
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

        public @Nullable Class<?> getDetailsType() throws ClassNotFoundException {
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
