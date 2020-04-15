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

class DeprecationTimeline {
    private final String messagePattern;
    private final GradleVersion targetVersion;

    private DeprecationTimeline(String messagePattern, GradleVersion targetVersion) {
        this.messagePattern = messagePattern;
        this.targetVersion = targetVersion;
    }

    static DeprecationTimeline willBeRemovedInVersion(GradleVersion version) {
        return new DeprecationTimeline("This is scheduled to be removed in %s.", version);
    }

    static DeprecationTimeline willBecomeAnErrorInVersion(GradleVersion version) {
        return new DeprecationTimeline("This will fail with an error in %s.", version);
    }

    static DeprecationTimeline behaviourWillBeRemovedInVersion(GradleVersion version) {
        return new DeprecationTimeline("This behaviour has been deprecated and is scheduled to be removed in %s.", version);
    }

    @Override
    public String toString() {
        return String.format(messagePattern, targetVersion);
    }
}
