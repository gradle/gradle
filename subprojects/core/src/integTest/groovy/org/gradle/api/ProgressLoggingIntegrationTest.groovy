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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture

class ProgressLoggingIntegrationTest extends AbstractIntegrationSpec {
    @org.junit.Rule
    ProgressLoggingFixture events = new ProgressLoggingFixture(executer, temporaryFolder)

    def "generates progress events during build"() {
        when:
        run()

        then:
        events.progressLogged("Evaluate settings")
        events.progressLogged("Configure project :")
        events.progressLogged("Task :help")
    }

    @ToBeFixedForConfigurationCache(because = "buildSrc is skipped")
    def "generates buildSrc progress events when there is a nested buildSrc build"() {
        when:
        run()

        then:
        !events.progressLogged("Build buildSrc")

        when:
        file("buildSrc/build.gradle") << "println 'buildSrc'"
        run()

        then:
        events.progressLogged("Build buildSrc")
    }
}
