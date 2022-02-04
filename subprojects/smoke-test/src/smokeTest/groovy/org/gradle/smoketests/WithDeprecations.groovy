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
    private static final VersionNumber AGP_VERSION_WITH_FIXED_NEW_WORKERS_API = VersionNumber.parse('4.2')

    private static final String ARTIFACT_TRANSFORM_DEPRECATION =
        "Registering artifact transforms extending ArtifactTransform has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. Implement TransformAction instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/artifact_transforms.html for more details."

    private static final String CONFIGURATION_AS_DEPENDENCY_DEPRECATION =
        "Adding a Configuration as a dependency is a confusing behavior which isn't recommended. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
            "If you're interested in inheriting the dependencies from the Configuration you are adding, you should use Configuration#extendsFrom instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:extendsFrom(org.gradle.api.artifacts.Configuration[]) for more details."

    private static final String ARCHIVE_NAME_DEPRECATION = "The AbstractArchiveTask.archiveName property has been deprecated. " +
        "This is scheduled to be removed in Gradle 8.0. Please use the archiveFileName property instead. " +
        "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archiveName for more details."

    private static final String WORKER_SUBMIT_DEPRECATION = "The WorkerExecutor.submit() method has been deprecated. This is scheduled to be removed in Gradle 8.0. " +
        "Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
        "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."

    private static final Function<String, String> FILE_TREE_FOR_EMPTY_SOURCES_DEPRECATION = { propertyName ->
        "Relying on FileTrees for ignoring empty directories when using @SkipWhenEmpty has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. " +
            "Annotate the property ${propertyName} with @IgnoreEmptyDirectories or remove @SkipWhenEmpty. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#empty_directories_file_tree"
    }

    private static final String JAVAEXEC_SET_MAIN_DEPRECATION = "The JavaExecHandleBuilder.setMain(String) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 8.0. " +
        "Please use the mainClass property instead. Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#java_exec_properties"

    void expectKotlinConfigurationAsDependencyDeprecation(SmokeTestGradleRunner runner, String version) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_WITHOUT_CONFIGURATION_DEPENDENCY, CONFIGURATION_AS_DEPENDENCY_DEPRECATION)
    }

    void expectKotlinArtifactTransformDeprecation(SmokeTestGradleRunner runner, String kotlinPluginVersion) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION)
    }

    void expectKotlinArchiveNameDeprecation(SmokeTestGradleRunner runner, String kotlinPluginVersion) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber.minor == 3, ARCHIVE_NAME_DEPRECATION)
    }

    void expectKotlinWorkerSubmitDeprecation(SmokeTestGradleRunner runner, boolean workers, String kotlinPluginVersion) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        runner.expectLegacyDeprecationWarningIf(workers && kotlinVersionNumber < KOTLIN_VERSION_USING_NEW_WORKERS_API, WORKER_SUBMIT_DEPRECATION)
    }

    void expectKotlin2JsPluginDeprecation(SmokeTestGradleRunner runner, String version) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse('1.4.0'),
            "The `kotlin2js` Gradle plugin has been deprecated."
        )
    }

    void expectKotlinParallelTasksDeprecation(SmokeTestGradleRunner runner, String version) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse('1.5.20'),
            "Project property 'kotlin.parallel.tasks.in.project' is deprecated."
        )
    }

    void expectKotlinDestinationDirPropertyDeprecation(SmokeTestGradleRunner runner, String version) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse('1.5.20'),
            "The AbstractCompile.destinationDir property has been deprecated. " +
                "This is scheduled to be removed in Gradle 8.0. " +
                "Please use the destinationDirectory property instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring"
        )
    }

    void expectAndroidFileTreeForEmptySourcesDeprecationWarnings(SmokeTestGradleRunner runner, String agpVersion, String... properties) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        properties.each {
            if (it == "sourceFiles" || it == "sourceDirs" || it == "inputFiles") {
                runner.expectDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY, FILE_TREE_FOR_EMPTY_SOURCES_DEPRECATION.apply(it), "https://issuetracker.google.com/issues/205285261")
            } else if (it == "resources") {
                runner.expectDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY, FILE_TREE_FOR_EMPTY_SOURCES_DEPRECATION.apply(it), "https://issuetracker.google.com/issues/204425803")
            } else if (it == "projectNativeLibs") {
                runner.expectLegacyDeprecationWarningIf(agpVersionNumber.getMajor() == 4, FILE_TREE_FOR_EMPTY_SOURCES_DEPRECATION.apply(it))
            }
        }
    }

    void expectAndroidWorkerExecutionSubmitDeprecationWarning(SmokeTestGradleRunner runner, String agpVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        runner.expectLegacyDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_NEW_WORKERS_API, WORKER_SUBMIT_DEPRECATION)
    }

    void expectAndroidLintPerVariantTaskAllInputsDeprecation(SmokeTestGradleRunner runner, String agpVersion, String artifact) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersion.startsWith("4.1"),
            "In plugin 'com.android.internal.version-check' type 'com.android.build.gradle.tasks.LintPerVariantTask' property 'allInputs' cannot be resolved:  " +
                "Cannot convert the provided notation to a File or URI: $artifact. " +
                "The following types/formats are supported:  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'. - A String or CharSequence URI, for example 'file:/usr/include'. - A File instance. - A Path instance. - A Directory instance. - A RegularFile instance. - A URI or URL instance. - A TextResource instance. " +
                "Reason: An input file collection couldn't be resolved, making it impossible to determine task inputs. " +
                "Please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/validation_problems.html#unresolvable_input for more details about this problem. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
                "Execution optimizations are disabled to ensure correctness. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
        )
    }

    void expectAsciiDocDeprecationWarnings(SmokeTestGradleRunner smokeTestGradleRunner) {
        smokeTestGradleRunner
            .expectDeprecationWarning(JAVAEXEC_SET_MAIN_DEPRECATION, "https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/602")
            .expectDeprecationWarning(FILE_TREE_FOR_EMPTY_SOURCES_DEPRECATION.apply("sourceFileTree"), "https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/629")
    }
}
