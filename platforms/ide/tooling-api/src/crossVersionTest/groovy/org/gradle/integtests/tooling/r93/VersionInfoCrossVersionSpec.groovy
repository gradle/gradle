/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r93

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.build.VersionInfo
import org.gradle.tooling.model.build.Help

@TargetGradleVersion(">=9.3")
class VersionInfoCrossVersionSpec extends ToolingApiSpecification {

    def "can fetch VersionInfo model"() {
        when:
        def versionInfo = withConnection { connection ->
            def builder = connection.model(VersionInfo.class)
            def env = new HashMap(System.getenv())
            env.remove('DEVELOCITY_ACCESS_KEY')
            builder.setEnvironmentVariables(env)
            builder.get()
        }

        then:
        versionInfo != null
        versionInfo.versionOutput.contains("Gradle")
    }

    def "can fetch Help model"() {
        when:
        def help = withConnection { connection ->
            def builder = connection.model(Help.class)
            def env = new HashMap(System.getenv())
            env.remove('DEVELOCITY_ACCESS_KEY')
            builder.setEnvironmentVariables(env)
            builder.get()
        }

        then:
        help != null
        help.helpOutput.contains("USAGE: gradle")
    }
}
