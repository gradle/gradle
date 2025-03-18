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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType
import org.gradle.api.JavaVersion

@SelfType(AbstractIntegrationSpec)
trait Java24UnsafeWarningFixture {
    /**
     * Documents that {@code user} uses {@code LoadingCache} which needs a new Guava version to stop emitting deprecation warnings.
     *
     * @param user the user of the {@code LoadingCache}
     * @return a message documenting the use of {@code LoadingCache}
     */
    String usesLoadingCache(String user) {
        return "$user uses LoadingCache which needs a new Guava version with https://github.com/google/guava/issues/6806"
    }

    /**
     * Expect that the test produces the Java 24+ deprecation warning for using a terminally deprecated method in {@code sun.misc.Unsafe}, if the current Java version is 24 or higher.
     *
     * @param reason the reason this message is expected
     */
    @SuppressWarnings("unused")
    void maybeExpectUnsuppressableUnsafeDeprecationWarning(String reason) {
        if (JavaVersion.current() >= JavaVersion.VERSION_24) {
            executer.expectDeprecationWarning("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
        }
    }

}
