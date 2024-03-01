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

package org.gradle.jvm.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.spockframework.util.TextUtil

class MavenToolchainsIntegrationTest extends AbstractIntegrationSpec {
    def "logs at info level when invalid toolchain file is provided"() {
        def toolchainsFile = file("toolchains.xml")
        executer.withArgument("-Porg.gradle.java.installations.maven-toolchains-file=${TextUtil.escape(toolchainsFile.absolutePath)}")
        executer.withToolchainDetectionEnabled()
        toolchainsFile << "not xml"

        when:
        succeeds 'javaToolchains', '--info'

        then:
        outputContains "Java Toolchain auto-detection failed to parse Maven Toolchains located at ${toolchainsFile}. Content is not allowed in prolog."
    }
}
