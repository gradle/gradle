/*
 * Copyright 2022 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;

public class DocumentedFailure {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Documentation.AbstractBuilder<Builder> {
        private String summary;
        private String advice;
        private String contextualAdvice;
        private Documentation documentation;

        private Builder() {}

        public Builder withSummary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder withContext(String contextualAdvice) {
            this.contextualAdvice = contextualAdvice;
            return this;
        }

        public Builder withAdvice(String advice) {
            this.advice = advice;
            return this;
        }

        @Override
        protected Builder withDocumentation(Documentation documentation) {
            this.documentation = documentation;
            return this;
        }

        public GradleException build() {
            StringBuilder outputBuilder = new StringBuilder(summary);
            append(outputBuilder, contextualAdvice);
            append(outputBuilder, advice);
            append(outputBuilder, documentation.consultDocumentationMessage());
            return new GradleException(outputBuilder.toString());
        }

        private static void append(StringBuilder outputBuilder, @Nullable String message) {
            if (!StringUtils.isEmpty(message)) {
                outputBuilder.append(" ").append(message);
            }
        }
    }
}
