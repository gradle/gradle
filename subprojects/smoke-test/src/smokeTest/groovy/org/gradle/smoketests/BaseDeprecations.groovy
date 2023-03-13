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

class BaseDeprecations {

    public static final String WORKER_SUBMIT_DEPRECATION = "The WorkerExecutor.submit() method has been deprecated. This is scheduled to be removed in Gradle 8.0. " +
        "Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
        "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."

    public static final String PROJECT_CONVENTION_DEPRECATION = "The Project.getConvention() method has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"

    public static final String CONVENTION_TYPE_DEPRECATION = "The org.gradle.api.plugins.Convention type has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"

    public static final String JAVAEXEC_SET_MAIN_DEPRECATION = "The JavaExecHandleBuilder.setMain(String) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 8.0. " +
        "Please use the mainClass property instead. Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#java_exec_properties"

    final SmokeTestGradleRunner runner

    BaseDeprecations(SmokeTestGradleRunner runner) {
        this.runner = runner
    }

    protected static String getFileTreeForEmptySourcesDeprecationForProperty(String propertyName) {
        "Relying on FileTrees for ignoring empty directories when using @SkipWhenEmpty has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. " +
            "Annotate the property ${propertyName} with @IgnoreEmptyDirectories or remove @SkipWhenEmpty. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#empty_directories_file_tree"
    }
}
