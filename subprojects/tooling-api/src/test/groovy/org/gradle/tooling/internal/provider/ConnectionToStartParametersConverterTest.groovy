/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider

import spock.lang.Specification
import org.gradle.tooling.internal.protocol.ConnectionParametersVersion1

class ConnectionToStartParametersConverterTest extends Specification {
    final ConnectionParametersVersion1 parameters = Mock()
    final ConnectionToStartParametersConverter converter = new ConnectionToStartParametersConverter()

    def setsCurrentDirectory() {
        File projectDir = new File('project-dir')

        when:
        def startParam = converter.convert(parameters)

        then:
        _ * parameters.projectDir >> projectDir
        startParam.currentDir == projectDir.canonicalFile
    }

    def setsGradleUserHomeDirIfSpecified() {
        File userHomeDir = new File('user-home')

        when:
        def startParam = converter.convert(parameters)

        then:
        _ * parameters.gradleUserHomeDir >> userHomeDir
        startParam.gradleUserHomeDir == userHomeDir.canonicalFile
    }
}
