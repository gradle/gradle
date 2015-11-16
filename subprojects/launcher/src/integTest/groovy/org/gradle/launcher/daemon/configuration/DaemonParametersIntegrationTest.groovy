/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.launcher.daemon.configuration

import org.gradle.initialization.BuildLayoutParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.UsesNativeServices

@UsesNativeServices
class DaemonParametersIntegrationTest extends AbstractIntegrationSpec {

    def honorsGradleUserHomeDir() {
        setup:
        def userHomeDir = testDirectory.createDir('userDir')
        BuildLayoutParameters layoutParams = new BuildLayoutParameters()
        layoutParams.gradleUserHomeDir = userHomeDir
        DaemonParameters parametersWithBaseDir = new DaemonParameters(layoutParams)

        when:
        def result = parametersWithBaseDir.baseDir

        then:
        result == userHomeDir.file('daemon')
    }
}
