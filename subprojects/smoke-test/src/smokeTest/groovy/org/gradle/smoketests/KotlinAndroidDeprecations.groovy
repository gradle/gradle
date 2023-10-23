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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.smoketests.KotlinRunnerFactory.ParallelTasksInProject
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION

class KotlinAndroidDeprecations extends BaseDeprecations implements WithKotlinDeprecations, WithAndroidDeprecations {
    public static final DocumentationRegistry DOC_REGISTRY = new DocumentationRegistry()

    KotlinAndroidDeprecations(SmokeTestGradleRunner runner) {
        super(runner)
    }
    private static final VersionNumber KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY = VersionNumber.parse('1.4.31')
    private static final String CONFIGURATION_AS_DEPENDENCY_DEPRECATION =
        "Adding a Configuration as a dependency is a confusing behavior which isn't recommended. " +
            "This behavior is scheduled to be removed in Gradle 8.0. " +
            "If you're interested in inheriting the dependencies from the Configuration you are adding, you should use Configuration#extendsFrom instead. " +
            String.format(RECOMMENDATION,"information",  DOC_REGISTRY.getDslRefForProperty("org.gradle.api.artifacts.Configuration", "extendsFrom(org.gradle.api.artifacts.Configuration[])"))

    void expectKotlinConfigurationAsDependencyDeprecation(VersionNumber kotlinVersionNumber) {
        runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY, CONFIGURATION_AS_DEPENDENCY_DEPRECATION)
    }

    void expectAndroidOrKotlinWorkerSubmitDeprecation(VersionNumber androidPluginVersionNumber, ParallelTasksInProject parallelTasksInProject, VersionNumber kotlinPluginVersionNumber) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesOldWorkerApi(androidPluginVersionNumber.toString()) || (parallelTasksInProject.isPropertyPresent() && kotlinPluginUsesOldWorkerApi(kotlinPluginVersionNumber)), WORKER_SUBMIT_DEPRECATION)
    }

    void expectBasePluginExtensionArchivesBaseNameDeprecation(VersionNumber kotlinVersionNumber, VersionNumber androidVersionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        expectKotlinBasePluginExtensionArchivesBaseNameDeprecation(kotlinVersionNumber, action)
        runner.expectLegacyDeprecationWarningIf(
            kotlinVersionNumber >= VersionNumber.parse('1.7.0') && androidVersionNumber < VersionNumber.parse('7.4.0'),
            "The BasePluginExtension.archivesBaseName property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the archivesName property instead. " +
                "For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.plugins.BasePluginExtension.html#org.gradle.api.plugins.BasePluginExtension:archivesName in the Gradle documentation.",
            action
        )
    }
}
