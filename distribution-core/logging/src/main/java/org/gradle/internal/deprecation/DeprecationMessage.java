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

package org.gradle.internal.deprecation;

class DeprecationMessage {

    private final String summary;
    private final String removalDetails;
    private final String advice;
    private final String context;
    private final Documentation documentation;
    private final DeprecatedFeatureUsage.Type usageType;

    DeprecationMessage(String summary, String removalDetails, String advice, String context, Documentation documentation, DeprecatedFeatureUsage.Type usageType) {
        this.summary = summary;
        this.removalDetails = removalDetails;
        this.advice = advice;
        this.context = context;
        this.documentation = documentation;
        this.usageType = usageType;
    }

    DeprecatedFeatureUsage toDeprecatedFeatureUsage(Class<?> calledFrom) {
        return new DeprecatedFeatureUsage(summary, removalDetails, advice, context, documentation, usageType, calledFrom);
    }

}
