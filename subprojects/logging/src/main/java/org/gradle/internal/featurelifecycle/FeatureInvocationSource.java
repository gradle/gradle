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

package org.gradle.internal.featurelifecycle;

import java.util.Objects;

/**
 * Represents an invocation of a deprecated feature.
 */
class FeatureInvocationSource {
    enum SourceType {
        BUILD_SRC,
        SCRIPT,
        THIRD_PARTY_JAR,
        GRADLE_INTERNAL,
        UNKNOWN
    }

    private String message;
    private SourceType sourceType;

    FeatureInvocationSource(String message, SourceType sourceType) {
        this.message = message;
        this.sourceType = sourceType;
    }

    boolean isFromThirdPartyPlugin() {
        return sourceType == SourceType.THIRD_PARTY_JAR;
    }

    boolean isUnknown() {
        return sourceType == SourceType.UNKNOWN;
    }

    SourceType getSourceType() {
        return sourceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FeatureInvocationSource that = (FeatureInvocationSource) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }
}
