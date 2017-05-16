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
import com.google.common.collect.Lists;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuildOperationRecord {

    public final Object id;
    public final Object parentId;
    public final String displayName;
    public final long startTime;
    public final long endTime;
    public final Map<String, ?> details;
    public final String detailsClassName;
    public final Map<String, ?> result;
    public final String resultClassName;
    public final String failure;

    public final List<BuildOperationRecord> children;

    BuildOperationRecord(
        Object id,
        Object parentId,
        String displayName,
        long startTime,
        long endTime,
        Map<String, ?> details,
        String detailsClassName,
        Map<String, ?> result,
        String resultClassName,
        String failure,
        List<BuildOperationRecord> children
    ) {
        this.id = id;
        this.parentId = parentId;
        this.displayName = displayName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.details = details;
        this.detailsClassName = detailsClassName;
        this.result = result;
        this.resultClassName = resultClassName;
        this.failure = failure;
        this.children = children;
    }

    BuildOperationRecord(Map<String, ?> serialized) {
        this.id = serialized.get("id");
        this.parentId = serialized.get("parentId");
        this.displayName = (String) serialized.get("displayName");
        this.startTime = (Long) serialized.get("startTime");
        this.endTime = (Long) serialized.get("endTime");

        this.detailsClassName = (String) serialized.get("detailsClassName");
        @SuppressWarnings("unchecked") Map<String, ?> detailsMap = (Map<String, ?>) serialized.get("details");
        this.details = detailsMap;

        this.resultClassName = (String) serialized.get("resultClassName");
        @SuppressWarnings("unchecked") Map<String, ?> resultMap = (Map<String, ?>) serialized.get("result");
        this.result = resultMap;

        this.failure = (String) serialized.get("failure");

        @SuppressWarnings("unchecked") List<Map<String, ?>> children = (List<Map<String, ?>>) serialized.get("children");
        this.children = ImmutableList.copyOf(Lists.transform(children, new Function<Map<String, ?>, BuildOperationRecord>() {
            @Override
            public BuildOperationRecord apply(Map<String, ?> input) {
                return new BuildOperationRecord(input);
            }
        }));
    }

    Map<String, ?> toSerializable() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("displayName", displayName);

        map.put("id", id);

        if (parentId != null) {
            map.put("parentId", parentId);
        }

        map.put("startTime", startTime);
        map.put("endTime", startTime);
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

    public Class<?> getDetailsType() throws ClassNotFoundException {
        return detailsClassName == null ? null : getClass().getClassLoader().loadClass(detailsClassName);
    }

    public Class<?> getResultType() throws ClassNotFoundException {
        return resultClassName == null ? null : getClass().getClassLoader().loadClass(resultClassName);
    }

    @Override
    public String toString() {
        return "BuildOperationRecord{" + displayName + '}';
    }
}
