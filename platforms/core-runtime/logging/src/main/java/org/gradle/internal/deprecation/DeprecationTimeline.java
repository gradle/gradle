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

import javax.annotation.Nullable;

class DeprecationTimeline {
    private final String messagePattern;
    private final GradleVersion targetVersion;
    private final String message;

    private DeprecationTimeline(String messagePattern, GradleVersion targetVersion, @Nullable String message) {
        this.messagePattern = messagePattern;
        this.targetVersion = targetVersion;
        this.message = message;
    }

    private DeprecationTimeline(String messagePattern, GradleVersion targetVersion) {
        this(messagePattern, targetVersion, null);
    }

    static DeprecationTimeline willBeRemovedInVersion(GradleVersion version) {
        return new DeprecationTimeline("This is scheduled to be removed in %s.", version);
    }

    static DeprecationTimeline willBecomeAnErrorInVersion(GradleVersion version) {
        return new DeprecationTimeline("This will fail with an error in %s.", version);
    }

    static DeprecationTimeline behaviourWillBeRemovedInVersion(GradleVersion version) {
        return new DeprecationTimeline("This behavior is scheduled to be removed in %s.", version);
    }

    static DeprecationTimeline willChangeInVersion(GradleVersion version) {
        return new DeprecationTimeline("This will change in %s.", version);
    }

    static DeprecationTimeline startingWithVersion(GradleVersion version, String message) {
        return new DeprecationTimeline("Starting with %s, %s.", version, message);
    }

    @Override
    public String toString() {
        return message == null ? String.format(messagePattern, targetVersion) : String.format(messagePattern, targetVersion, message);
    }
}
