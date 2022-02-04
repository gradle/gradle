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

import java.util.function.Function

trait WithDeprecations {

    private static final VersionNumber KOTLIN_VERSION_USING_NEW_TRANSFORMS_API = VersionNumber.parse('1.4.20')
    private static final VersionNumber KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY = VersionNumber.parse('1.4.31')
    private static final VersionNumber KOTLIN_VERSION_USING_NEW_WORKERS_API = VersionNumber.parse('1.5.0')
    private static final VersionNumber AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY = VersionNumber.parse('7.2.0')

    private static final String ARTIFACT_TRANSFORM_DEPRECATION_WARNING =
        "Registering artifact transforms extending ArtifactTransform has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. Implement TransformAction instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/artifact_transforms.html for more details."

    private static String CONFIGURATION_AS_DEPENDENCY_DEPRECATION_WARNING =
        "Adding a Configuration as a dependency is a confusing behavior which isn't recommended. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
            "If you're interested in inheriting the dependencies from the Configuration you are adding, you should use Configuration#extendsFrom instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:extendsFrom(org.gradle.api.artifacts.Configuration[]) for more details."

    private static final ARCHIVE_NAME_DEPRECATION_WARNING = "The AbstractArchiveTask.archiveName property has been deprecated. " +
        "This is scheduled to be removed in Gradle 8.0. Please use the archiveFileName property instead. " +
        "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archiveName for more details."

    private static final WORKER_SUBMIT_DEPRECATION_WARNING = "The WorkerExecutor.submit() method has been deprecated. This is scheduled to be removed in Gradle 8.0. " +
        "Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
        "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."

    private static final Function<String, String> DEPRECATION_OF_FILE_TREE_FOR_EMPTY_SOURCES = { propertyName ->
        "Relying on FileTrees for ignoring empty directories when using @SkipWhenEmpty has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. " +
            "Annotate the property ${propertyName} with @IgnoreEmptyDirectories or remove @SkipWhenEmpty. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#empty_directories_file_tree"
    }

    void expectKotlinDeprecationWarnings(SmokeTestGradleRunner smokeTestGradleRunner, boolean workers, String kotlinPluginVersion) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        smokeTestGradleRunner
            .expectLegacyDeprecationWarningIf(kotlinVersionNumber.minor == 3, ARCHIVE_NAME_DEPRECATION_WARNING)
            .expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY, CONFIGURATION_AS_DEPENDENCY_DEPRECATION_WARNING)
            .expectLegacyDeprecationWarningIf(workers && kotlinVersionNumber < KOTLIN_VERSION_USING_NEW_WORKERS_API, WORKER_SUBMIT_DEPRECATION_WARNING)
    }

    void expectAndroidFileTreeForEmptySourcesDeprecationWarnings(SmokeTestGradleRunner smokeTestGradleRunner, String agpVersion, String... properties) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        properties.each {
            String followup = it == "resources" ? "https://issuetracker.google.com/issues/204425803" : "https://issuetracker.google.com/issues/205285261"
            smokeTestGradleRunner.expectDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY, DEPRECATION_OF_FILE_TREE_FOR_EMPTY_SOURCES.apply(it), followup)
        }
    }

    String deprecationOfFileTreeForEmptySources(String propertyName) {
        return DEPRECATION_OF_FILE_TREE_FOR_EMPTY_SOURCES.apply(propertyName)
    }
}
