/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.r70

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

@TargetGradleVersion('>=7.0')
class BuildSrcCrossVersionSpec extends ToolingApiSpecification {

    def "buildSrc without settings file can execute standalone"() {
        given:
        def buildSrc = file("buildSrc")
        buildSrc.file("build.gradle") << ''

        when:
        def connection = toolingApi.connector(buildSrc).connect()
        buildSrc.file("settings.gradle").delete()

        then:
        connection.newBuild().forTasks("help").run()
    }
}
