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

package org.gradle.internal.deprecation;

import javax.annotation.Nullable;

public class DeprecationInfo {

    private final Class<?> methodClass;
    private final String methodWithParams;
    private final String removedInVersion;
    private final String advice;
    private final String summary;
    private final String replaceWith;

    public DeprecationInfo(DeprecationInfoBuilder builder) {
        this.methodClass = builder.methodClass;
        this.methodWithParams = builder.methodWithParams;
        this.removedInVersion = builder.removedInVersion;
        this.advice = builder.advice;
        this.summary = builder.summary;
        replaceWith = builder.replaceWith;
    }

    public @Nullable Class<?> getMethodClass() {
        return methodClass;
    }

    public @Nullable String getMethodWithParams() {
        return methodWithParams;
    }

    public @Nullable String getRemovedInVersion() {
        return removedInVersion;
    }

    public @Nullable String getAdvice() {
        return advice;
    }

    public @Nullable String getSummary() {
        return summary;
    }

    public @Nullable String getReplaceWith() {
        return replaceWith;
    }

    public static DeprecationInfoBuilder builder() {
        return new DeprecationInfoBuilder();
    }

    public static class DeprecationInfoBuilder {

        private Class<?> methodClass = null;
        private String methodWithParams = null;
        private String removedInVersion = null;
        private String advice;
        private String summary;
        private String replaceWith;

        public void methodDeprecation(Class<?> methodClass, String methodWithParams) {
            this.methodClass = methodClass;
            this.methodWithParams = methodWithParams;
        }

        public void removedInVersion(String removedInVersion) {
            this.removedInVersion = removedInVersion;
        }

        public DeprecationInfo build() {
            return new DeprecationInfo(this);
        }

        public void advice(String advice) {
            this.advice = advice;
        }

        public void summary(String summary) {
            this.summary = summary;
        }

        public void replaceWith(String replaceWith) {
            this.replaceWith = replaceWith;;
        }
    }
}
