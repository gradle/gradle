/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.util;

import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage;

public class DeprecationMessage {

    private final String summary;
    private final String removalDetails;
    private String advice;
    private String contextualAdvice;
    private String documentationReference;

    public DeprecationMessage(String summary, String removalDetails) {
        this.summary = summary;
        this.removalDetails = removalDetails;
    }

    public DeprecationMessage withAdvice(String advice) {
        this.advice = advice;
        return this;
    }

    public DeprecationMessage withContextualAdvice(String contextualAdvice) {
        this.contextualAdvice = contextualAdvice;
        return this;
    }

    public DeprecationMessage withDocumentationReference(String documentationReference) {
        this.documentationReference = documentationReference;
        return this;
    }

    DeprecatedFeatureUsage toDeprecatedFeatureUsage(DeprecatedFeatureUsage.Type usageType, Class<?> calledFrom) {
        return new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, usageType, calledFrom);
    }
}
