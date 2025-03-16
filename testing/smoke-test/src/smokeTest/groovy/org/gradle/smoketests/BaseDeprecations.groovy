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

import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION

class BaseDeprecations {
    public static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    public static final String ABSTRACT_ARCHIVE_TASK_ARCHIVE_PATH_DEPRECATION = "The AbstractArchiveTask.archivePath property has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Please use the archiveFile property instead. " +
        String.format(RECOMMENDATION, "information", DOCUMENTATION_REGISTRY.getDslRefForProperty("org.gradle.api.tasks.bundling.AbstractArchiveTask","archivePath"))

    public static final String CONVENTION_TYPE_DEPRECATION = "The org.gradle.api.plugins.Convention type has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Consult the upgrading guide for further information: " +
        DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_8","deprecated_access_to_conventions")

    public static final String JAVA_PLUGIN_CONVENTION_DEPRECATION = "The org.gradle.api.plugins.JavaPluginConvention type has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Consult the upgrading guide for further information: " +
        DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_8","java_convention_deprecation")

    final SmokeTestGradleRunner runner

    BaseDeprecations(SmokeTestGradleRunner runner) {
        this.runner = runner
    }
}
