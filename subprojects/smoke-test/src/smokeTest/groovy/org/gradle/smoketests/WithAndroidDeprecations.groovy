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

import groovy.transform.SelfType
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations implements WithReportDeprecations {
    private static final VersionNumber AGP_VERSION_WITH_FIXED_NEW_WORKERS_API = VersionNumber.parse('4.2')

    boolean androidPluginUsesOldWorkerApi(String agpVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        agpVersionNumber < AGP_VERSION_WITH_FIXED_NEW_WORKERS_API
    }

    void expectAndroidWorkerExecutionSubmitDeprecationWarning(String agpVersion) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesOldWorkerApi(agpVersion), WORKER_SUBMIT_DEPRECATION)
    }
<<<<<<< HEAD

    void expectAndroidIncrementalTaskInputsDeprecation(String agpVersion) {
        def agpVersionNumber = VersionNumber.parse(agpVersion)
        def method = agpVersionNumber < VersionNumber.parse("4.2")
            ? 'taskAction$gradle'
            : 'taskAction$gradle_core'
        // https://issuetracker.google.com/218478028
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.3.0-alpha08"),
            getIncrementalTaskInputsDeprecationWarning("IncrementalTask.${method}"))
    }

    void expectCompileOptionsAnnotationProcessorGeneratedSourcesDirectoryDeprecation(String agpVersion) {
        runner.expectLegacyDeprecationWarningIf(VersionNumber.parse(agpVersion) <= VersionNumber.parse("4.2.2"),
                "The CompileOptions.annotationProcessorGeneratedSourcesDirectory property has been deprecated." +
                " This is scheduled to be removed in Gradle 9.0." +
                " Please use the generatedSourceOutputDirectory property instead." +
                " See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:annotationProcessorGeneratedSourcesDirectory for more details.")
    }
=======
>>>>>>> master
}
