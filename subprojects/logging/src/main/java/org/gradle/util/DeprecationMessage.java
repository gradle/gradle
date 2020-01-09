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

import com.google.common.base.Joiner;
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage;

import java.util.List;

import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getRemovalDetails;
import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getWillBecomeErrorMessage;

public class DeprecationMessage {

    private final String summary;
    private final String removalDetails;
    private String advice;
    private String contextualAdvice;
    private String documentationReference;

    private DeprecatedFeatureUsage.Type usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT;

    public static DeprecationMessage thisHasBeenDeprecated(String summary) {
        return new DeprecationMessage(summary, String.format("This has been deprecated and %s", getRemovalDetails()));
    }

    public static DeprecationMessage specificThingHasBeenDeprecated(String thing) {
        return new DeprecationMessage(String.format("%s has been deprecated.", thing), thisWillBeRemovedMessage());
    }

    public static DeprecationMessage configurationHasBeenReplaced(String configurationName, SingleMessageLogger.ConfigurationDeprecationType deprecationType, List<String> replacements) {
        String summary = String.format("The %s configuration has been deprecated for %s.", configurationName, deprecationType.displayName());
        String suggestion = String.format("Please %s the %s configuration instead.", deprecationType.usage, Joiner.on(" or ").join(replacements));
        DeprecationMessage deprecationMessage = new DeprecationMessage(summary, thisWillBecomeAnError()).withAdvice(suggestion);
        if (!deprecationType.inUserCode) {
            deprecationMessage = deprecationMessage.withIndirectUsage();
        }
        return deprecationMessage;
    }

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

    public DeprecationMessage withIndirectUsage() {
        this.usageType = DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT;
        return this;
    }

    public DeprecationMessage withBuildInvocation() {
        this.usageType = DeprecatedFeatureUsage.Type.BUILD_INVOCATION;
        return this;
    }

    DeprecatedFeatureUsage toDeprecatedFeatureUsage(Class<?> calledFrom) {
        return new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, usageType, calledFrom);
    }

    private static String thisWillBeRemovedMessage() {
        return String.format("This %s", getRemovalDetails());
    }

    private static String thisWillBecomeAnError() {
        return getWillBecomeErrorMessage();
    }
}
