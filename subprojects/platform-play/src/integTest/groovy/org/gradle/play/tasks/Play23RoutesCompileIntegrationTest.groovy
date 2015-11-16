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

package org.gradle.play.tasks

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.play.integtest.fixtures.PlayCoverage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@TargetCoverage({ PlayCoverage.PLAY23_OR_EARLIER })
@Requires(TestPrecondition.JDK7_OR_LATER)
class Play23RoutesCompileIntegrationTest extends AbstractRoutesCompileIntegrationTest {
    @Override
    def getRoutesJavaFileNameTemplate(String packageName, String namespace) {
        return "${namespace ? namespace + '/' :''}controllers/${packageName ? packageName + '/' :''}routes.java"
    }

    @Override
    def getRoutesReverseFileNameTemplate(String packageName, String namespace) {
        return "${packageName ? packageName + '/' :''}routes_reverseRouting.scala"
    }

    @Override
    def getRoutesScalaFileNameTemplate(String packageName, String namespace) {
        return "${packageName ? packageName + '/' :''}routes_routing.scala"
    }

    @Override
    def getOtherRoutesFilesTemplates() {
        return []
    }

    def "trying to use injected router with older versions of Play produces reasonable error"() {
        given:
        withRoutesTemplate()

        buildFile << """
model {
    components {
        play {
            injectedRoutesGenerator = true
        }
    }
}
"""
        expect:
        fails("assemble")
        and:
        errorOutput.contains("Injected routers are only supported in Play 2.4 or newer.")
    }
}
