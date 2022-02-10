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
import org.gradle.util.internal.VersionNumber

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {
    private static final VersionNumber AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY = VersionNumber.parse('7.1.1')
    private static final VersionNumber AGP_VERSION_WITH_FIXED_NEW_WORKERS_API = VersionNumber.parse('4.2')

    boolean androidPluginUsesOldWorkerApi(String agpVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        agpVersionNumber < AGP_VERSION_WITH_FIXED_NEW_WORKERS_API
    }

    void expectAndroidWorkerExecutionSubmitDeprecationWarning(String agpVersion) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesOldWorkerApi(agpVersion), WORKER_SUBMIT_DEPRECATION)
    }

    void expectAndroidFileTreeForEmptySourcesDeprecationWarnings(String agpVersion, String... properties) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        properties.each {
            if (it == "sourceFiles" || it == "sourceDirs" || it == "inputFiles") {
                runner.expectDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY, getFileTreeForEmptySourcesDeprecationForProperty(it), "https://issuetracker.google.com/issues/205285261")
            } else if (it == "resources") {
                runner.expectDeprecationWarningIf(agpVersionNumber.getBaseVersion() < AGP_VERSION_WITH_FIXED_SKIP_WHEN_EMPTY, getFileTreeForEmptySourcesDeprecationForProperty(it), "https://issuetracker.google.com/issues/204425803")
            } else if (it == "projectNativeLibs") {
                runner.expectLegacyDeprecationWarningIf(agpVersionNumber.getMajor() == 4, getFileTreeForEmptySourcesDeprecationForProperty(it))
            }
        }
    }
}
