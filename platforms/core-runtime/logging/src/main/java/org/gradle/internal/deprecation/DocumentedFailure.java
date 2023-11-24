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
import org.gradle.api.problems.DocLink;
import org.gradle.internal.exceptions.Contextual;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public class DocumentedFailure {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Documentation.AbstractBuilder<Builder> {
        private String summary;
        private String advice;
        private String contextualAdvice;
        private DocLink documentation;

        private Builder() {}

        @CheckReturnValue
        public Builder withSummary(String summary) {
            this.summary = summary;
            return this;
        }

        @CheckReturnValue
        public Builder withContext(String contextualAdvice) {
            this.contextualAdvice = contextualAdvice;
            return this;
        }

        @CheckReturnValue
        public Builder withAdvice(String advice) {
            this.advice = advice;
            return this;
        }

        @Override
        @CheckReturnValue
        public Builder withDocumentation(DocLink documentation) {
            this.documentation = documentation;
            return this;
        }

        @CheckReturnValue
        public GradleException build() {
            return build(null);
        }

        @CheckReturnValue
        public GradleException build(@Nullable Throwable cause) {
            StringBuilder outputBuilder = new StringBuilder(summary);
            append(outputBuilder, contextualAdvice);
            append(outputBuilder, advice);
            append(outputBuilder, documentation.getConsultDocumentationMessage());
            return cause == null
                ? new GradleException(outputBuilder.toString())
                : new DocumentedExceptionWithCause(outputBuilder.toString(), cause);
        }

        private static void append(StringBuilder outputBuilder, @Nullable String message) {
            if (!StringUtils.isEmpty(message)) {
                outputBuilder.append(" ").append(message);
            }
        }
    }

    @Contextual
    public static class DocumentedExceptionWithCause extends GradleException {
        public DocumentedExceptionWithCause(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
