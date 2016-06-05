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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;
import org.gradle.util.GradleVersion;

/**
 * Thrown when a build was executed with a target Gradle version that does not support a specific feature.
 *
 * @since 2.11
 * @see GradleRunner#withGradleVersion(java.lang.String)
 * @see GradleRunner#withGradleInstallation(java.io.File)
 * @see GradleRunner#withGradleDistribution(java.net.URI)
 */
@Incubating
public class UnsupportedFeatureException extends RuntimeException {
    public UnsupportedFeatureException(String feature, GradleVersion targetGradleVersion, GradleVersion minSupportedGradleVersion) {
        super(String.format("The version of Gradle you are using (%s) does not %s. Support for this is available in Gradle %s and all later versions.",
              targetGradleVersion.getVersion(), feature, minSupportedGradleVersion.getVersion()));
    }

    public UnsupportedFeatureException(String message) {
        super(message);
    }
}
