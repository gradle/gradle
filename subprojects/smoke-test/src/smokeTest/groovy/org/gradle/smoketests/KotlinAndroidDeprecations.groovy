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

package org.gradle.smoketests

import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

class KotlinAndroidDeprecations extends BaseDeprecations implements WithKotlinDeprecations, WithAndroidDeprecations {
    KotlinAndroidDeprecations(SmokeTestGradleRunner runner) {
        super(runner)
    }
    private static final VersionNumber KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY = VersionNumber.parse('1.4.31')
    private static final String CONFIGURATION_AS_DEPENDENCY_DEPRECATION =
        "Adding a Configuration as a dependency is a confusing behavior which isn't recommended. " +
            "This behavior is scheduled to be removed in Gradle 8.0. " +
            "If you're interested in inheriting the dependencies from the Configuration you are adding, you should use Configuration#extendsFrom instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:extendsFrom(org.gradle.api.artifacts.Configuration[]) for more details."

    void expectKotlinConfigurationAsDependencyDeprecation(String version) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY, CONFIGURATION_AS_DEPENDENCY_DEPRECATION)
    }

    void expectAndroidOrKotlinWorkerSubmitDeprecation(String agpVersion, boolean kotlinWorkers, String kotlinVersion) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesOldWorkerApi(agpVersion) || (kotlinWorkers && kotlinPluginUsesOldWorkerApi(kotlinVersion)), WORKER_SUBMIT_DEPRECATION)
    }
}
