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

import org.gradle.util.GradleVersion;

final class Messages {
    private static String isScheduledToBeRemovedMessage;
    private static String willBecomeErrorMessage;
    private static String thisIsScheduledToBeRemoved;
    private static String thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved;

    private Messages() {
    }

    static String xHasBeenDeprecated(String x) {
        return String.format("%s has been deprecated.", x);
    }

    static String pleaseUseThisMethodInstead(String replacement) {
        return String.format("Please use the %s method instead.", replacement);
    }

    static String thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved() {
        if (thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved == null) {
            thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved = String.format("This behaviour has been deprecated and %s", isScheduledToBeRemoved());
        }
        return thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved;
    }

    static String thisIsScheduledToBeRemoved() {
        if (thisIsScheduledToBeRemoved == null) {
            thisIsScheduledToBeRemoved = String.format("This %s", isScheduledToBeRemoved());
        }
        return thisIsScheduledToBeRemoved;
    }

    static String isScheduledToBeRemoved() {
        if (isScheduledToBeRemovedMessage == null) {
            isScheduledToBeRemovedMessage = String.format("is scheduled to be removed in Gradle %s.", GradleVersion.current().getNextMajor().getVersion());
        }
        return isScheduledToBeRemovedMessage;
    }

    static String thisWillBecomeAnError() {
        if (willBecomeErrorMessage == null) {
            willBecomeErrorMessage = String.format("This will fail with an error in Gradle %s.", GradleVersion.current().getNextMajor().getVersion());
        }
        return willBecomeErrorMessage;
    }
}
