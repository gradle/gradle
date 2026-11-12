/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.problems.FileLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultStackTraceLocation implements StackTraceLocation {

    private final FileLocation location;
    private final List<StackTraceElement> stackTrace;
    private final @Nullable String callerApi;

    public DefaultStackTraceLocation(@Nullable FileLocation location, List<StackTraceElement> stackTrace) {
        this(location, stackTrace, null);
    }

    public DefaultStackTraceLocation(@Nullable FileLocation location, List<StackTraceElement> stackTrace, @Nullable String callerApi) {
        this.location = location;
        this.stackTrace = stackTrace;
        this.callerApi = callerApi;
    }

    @Nullable
    @Override
    public FileLocation getFileLocation() {
        return location;
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
        return stackTrace;
    }

    @Nullable
    @Override
    public String getCallerApi() {
        return callerApi;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultStackTraceLocation that = (DefaultStackTraceLocation) o;
        return (location == null ? that.location == null : location.equals(that.location))
            && stackTrace.equals(that.stackTrace)
            && (callerApi == null ? that.callerApi == null : callerApi.equals(that.callerApi));
    }

    @Override
    public int hashCode() {
        int result = location != null ? location.hashCode() : 0;
        result = 31 * result + stackTrace.hashCode();
        result = 31 * result + (callerApi != null ? callerApi.hashCode() : 0);
        return result;
    }
}
