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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.Requires

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
@Requires(adhoc = { AvailableJavaHomes.jdk6 })
@LeaksFileHandles
class ContinuousUnsupportedJavaVersionCrossVersionSpec extends ToolingApiSpecification {

    def "client receives error on unsupported platform"() {
        given:
        toolingApi.requireIsolatedDaemons()
        buildFile.text = "apply plugin: 'java'"

        when:
        withConnection {
            newBuild()
                .setJavaHome(AvailableJavaHomes.jdk6.javaHome)
                .withArguments("--continuous")
                .forTasks("build")
                .run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("Could not execute build using")
        e.cause.message == 'Continuous build requires Java 7 or later.'
    }

}
