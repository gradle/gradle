/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK6)
@ToolingApiVersion("current")
@TargetGradleVersion("current")
class ContinuousUnsupportedJavaVersionCrossVersionSpec extends ToolingApiSpecification {

    def "client receives appropriate error if continuous mode attempted on unsupported platform"() {
        given:
        buildFile.text = '''
apply plugin: 'java'
'''
        when:
        withConnection { ProjectConnection connection ->
            BuildLauncher launcher = connection.newBuild().withArguments("--continuous").forTasks("build")
            launcher.run()
        }
        then:
        GradleConnectionException gradleConnectionException = thrown()
        gradleConnectionException.cause.message == 'Continuous build requires Java 7 or later.'
    }
}
