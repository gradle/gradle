/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultProblemContext implements Serializable, ProblemContext {
    private final List<ProblemLocation> locations;
    private final String description;
    private final RuntimeException cause;
    private final Map<String, Object> additionalData;

    protected DefaultProblemContext(
        List<ProblemLocation> locations,
        @Nullable String description,
        @Nullable RuntimeException cause,
        Map<String, Object> additionalData
    ) {
        this.locations = ImmutableList.copyOf(locations);
        this.description = description;
        this.cause = cause;
        this.additionalData = ImmutableMap.copyOf(additionalData);
    }

    @Override
    public List<ProblemLocation> getLocations() {
        return locations;
    }

    @Override
    public String getDetails() {
        return description;
    }

    @Override
    public RuntimeException getException() {
        return cause;
    }

    @Override
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    private static boolean equals(@Nullable Object a, @Nullable Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemContext that = (DefaultProblemContext) o;
        return equals(locations, that.locations) &&
            equals(description, that.description) &&
            equals(cause, that.cause) &&
            equals(additionalData, that.additionalData);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{locations, description, cause, additionalData});
    }

}
