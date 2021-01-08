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
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuildOperationRecord {

    public static final Ordering<BuildOperationRecord> ORDERING = Ordering.natural()
        .onResultOf(new Function<BuildOperationRecord, Comparable>() {
            @Override
            public Comparable apply(BuildOperationRecord input) {
                return input.startTime;
            }
        })
        .compound(Ordering.natural().onResultOf(new Function<BuildOperationRecord, Comparable>() {
            @Override
            public Comparable apply(BuildOperationRecord input) {
                return input.id;
            }
        }));

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
        Map<String, Object> map = new LinkedHashMap<String, Object>();
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
            map.put("progress", Lists.transform(progress, new Function<Progress, Map<String, ?>>() {
                @Override
                public Map<String, ?> apply(Progress input) {
                    return input.toSerializable();
                }
            }));
        }

        if (!children.isEmpty()) {
            map.put("children", Lists.transform(children, new Function<BuildOperationRecord, Map<String, ?>>() {
                @Override
                public Map<String, ?> apply(BuildOperationRecord input) {
                    return input.toSerializable();
                }
            }));
        }

        return map;
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
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("time", time);

            if (details != null) {
                map.put("details", details);
                map.put("detailsClassName", detailsClassName);
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
