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
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import spock.lang.Ignore

class ContinuousModeCrossVersionSpec extends ToolingApiSpecification {

    @Ignore("Work in progress")
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "continuous mode is not supported yet"() {
        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.withArguments("--continuous").forTasks("tasks").run()
        }

        then:
        UnsupportedBuildArgumentException e = thrown()
        e.message.contains("Unknown command-line option '--continuous'.")
    }

    @Ignore
    def "client executes continuous build that succeeds, then responds to input changes and succeeds"() {}

    @Ignore
    def "client executes continuous build that succeeds, then responds to input changes and fails, then … and succeeds"() {}

    @Ignore
    def "client executes continuous build that fails, then responds to input changes and succeeds"() {}

    @Ignore
    def "client can cancel during execution of a continuous build"() {}

    @Ignore
    def "client can cancel while a continuous build is waiting for changes"() {}

    @Ignore
    def "client can request continuous mode when building a model, but request is effectively ignored"() {}

    @Ignore
    def "client can receive appropriate logging and progress events for subsequent builds in continuous mode"() {}

    @Ignore
    def "client receives appropriate error if continuous mode attempted on unsupported platform"() {}

    @Ignore
    def "logging does not include message to use ctrl-c to exit continuous mode"() {}
}
