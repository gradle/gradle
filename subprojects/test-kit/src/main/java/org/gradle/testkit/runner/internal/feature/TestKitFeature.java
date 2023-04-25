/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal.feature;

import org.gradle.util.GradleVersion;

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION;

public enum TestKitFeature {

    RUN_BUILDS(MINIMUM_SUPPORTED_GRADLE_VERSION),
    CAPTURE_BUILD_RESULT_TASKS(MINIMUM_SUPPORTED_GRADLE_VERSION),
    PLUGIN_CLASSPATH_INJECTION(MINIMUM_SUPPORTED_GRADLE_VERSION),
    CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG(MINIMUM_SUPPORTED_GRADLE_VERSION);

    private final GradleVersion since;

    TestKitFeature(GradleVersion since) {
        this.since = since;
    }

    public GradleVersion getSince() {
        return since;
    }
}
