/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.problems.AdditionalDataBuilder;

import javax.annotation.Nullable;

public class DefaultPropertyTraceData implements PropertyTraceData {
    private final String trace;

    public DefaultPropertyTraceData(String trace) {
        this.trace = trace;
    }

    public static AdditionalDataBuilder<PropertyTraceData> builder(@Nullable PropertyTraceData from) {
        if(from == null) {
            return new DefaultPropertyTraceDataBuilder();
        }
        return new DefaultPropertyTraceDataBuilder(from);
    }

    public static AdditionalDataBuilder<PropertyTraceData> builder() {
        return new DefaultPropertyTraceDataBuilder();
    }

    @Override
    public String getTrace() {
        return trace;
    }

    private static class DefaultPropertyTraceDataBuilder implements PropertyTraceDataSpec, AdditionalDataBuilder<PropertyTraceData> {
        private String trace;

        public DefaultPropertyTraceDataBuilder(PropertyTraceData from) {
            this.trace = from.getTrace();
        }

        public DefaultPropertyTraceDataBuilder() {
        }

        @Override
        public PropertyTraceDataSpec trace(String trace){
            this.trace = trace;
            return this;
        }

        @Override
        public PropertyTraceData build() {
            return new DefaultPropertyTraceData(trace);
        }
    }
}
