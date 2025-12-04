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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.BuildEnvironment

@ToolingApiVersion(">=9.4.0")
@TargetGradleVersion(">=9.4.0")
class BuildEnvironmentVersionInfoCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion('>=4.0 <9.4.0')
    def "cannot query old distribution for version info"() {
        when:
        withConnection { connection ->
            connection.getModel(BuildEnvironment).versionInfo
        }

        then:
        thrown(UnsupportedMethodException)
    }

    def "can fetch version info via model query"() {
        when:
        def versionInfo = withConnection { connection ->
            connection.getModel(BuildEnvironment).versionInfo
        }

        then:
        // line-by-line comparison to avoid very complex multi-line regex and get better reporting on failure
        def content = versionInfo.readLines()
        verifyAll {
            content.size() == 15
            content[0] ==   ''
            content[1] ==   '------------------------------------------------------------'
            content[2] ==~  'Gradle .*'
            content[3] ==   '------------------------------------------------------------'
            content[4] ==   ''
            content[5] ==~  'Build time:    .*'
            content[6] ==~  'Revision:      [a-f0-9]+'
            content[7] ==   ''
            content[8] ==~  'Kotlin:        .*'
            content[9] ==~  'Groovy:        .*'
            content[10] ==~ 'Ant:           .*'
            content[11] ==~ 'Launcher JVM:  .*'
            content[12] ==~ 'Daemon JVM:    .*'
            content[13] ==~ 'OS:            .*'
            content[14] ==  ''
        }
    }

    def "can fetch version info via build action"() {
        when:
        def versionInfo = withConnection { connection ->
            connection.action(new FetchBuildEnvironmentVersionInfoAction()).run()
        }
        then:
        // line-by-line comparison to avoid very complex multi-line regex and get better reporting on failure
        def content = versionInfo.readLines()
        verifyAll {
            content.size() == 14
            content[0] ==   ''
            content[1] ==   '------------------------------------------------------------'
            content[2] ==~  'Gradle .*'
            content[3] ==   '------------------------------------------------------------'
            content[4] ==   ''
            content[5] ==~  'Build time:    .*'
            content[6] ==~  'Revision:      [a-f0-9]+'
            content[7] ==   ''
            content[8] ==~  'Kotlin:        .*'
            content[9] ==~  'Groovy:        .*'
            content[10] ==~ 'Ant:           .*'
            content[11] ==~ 'Daemon JVM:    .*'
            content[12] ==~ 'OS:            .*'
            content[13] ==  ''
        }
    }
}
