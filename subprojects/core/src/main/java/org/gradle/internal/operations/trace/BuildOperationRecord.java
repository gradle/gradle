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

import java.util.Map;

public final class BuildOperationRecord {

    public final Object id;
    public final Object parentId;
    public final String displayName;
    public final long startTime;
    public final long endTime;
    public final Class<?> detailsType;
    public final Map<String, ?> details;
    public final Class<?> resultType;
    public final Map<String, ?> result;
    public final String failure;

    BuildOperationRecord(
        Object id,
        Object parentId,
        String displayName,
        long startTime,
        long endTime,
        Class<?> detailsType,
        Map<String, ?> details,
        Class<?> resultType,
        Map<String, ?> result,
        String failure
    ) {
        this.id = id;
        this.parentId = parentId;
        this.displayName = displayName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.detailsType = detailsType;
        this.details = details;
        this.resultType = resultType;
        this.result = result;
        this.failure = failure;
    }

}
