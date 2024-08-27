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

import javax.annotation.Nullable;

public class DefaultResolutionFailureData implements ResolutionFailureData {

    private final Object resolutionFailure;

    public static AdditionalDataBuilder<ResolutionFailureData> builder(@Nullable ResolutionFailureData resolutionFailure) {
        if (resolutionFailure == null) {
            return new DefaultResolutionFailureDataBuilder();
        }
        return new DefaultResolutionFailureDataBuilder(resolutionFailure);
    }

    public DefaultResolutionFailureData(Object resolutionFailure) {
        this.resolutionFailure = resolutionFailure;
    }

    @Override
    public Object getResolutionFailure() {
        return resolutionFailure;
    }

    private static class DefaultResolutionFailureDataBuilder implements ResolutionFailureDataSpec, AdditionalDataBuilder<ResolutionFailureData> {
        private Object failure;

        public DefaultResolutionFailureDataBuilder(ResolutionFailureData from) {
            this.failure = from.getResolutionFailure();
        }

        public DefaultResolutionFailureDataBuilder() {
        }

        @Override
        public ResolutionFailureDataSpec from(Object failure){
            this.failure = failure;
            return this;
        }

        @Override
        public ResolutionFailureData build() {
            return new DefaultResolutionFailureData(failure);
        }
    }
}
