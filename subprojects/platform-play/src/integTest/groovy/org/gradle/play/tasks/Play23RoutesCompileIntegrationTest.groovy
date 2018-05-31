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
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.play.integtest.fixtures.PlayCoverage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import static org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest.isPlay22

@TargetCoverage({ PlayCoverage.PLAY23_OR_EARLIER })
@Requires(TestPrecondition.JDK8)
@Issue("Play 2.2/2.3 don't support Java 9+")
class Play23RoutesCompileIntegrationTest extends AbstractRoutesCompileIntegrationTest {
    @Override
    def getJavaRoutesFileName(String packageName, String namespace) {
        return "${namespace ? namespace + '/' :''}controllers/${packageName ? packageName + '/' :''}routes.java"
    }

    @Override
    def getReverseRoutesFileName(String packageName, String namespace) {
        return "${packageName ? packageName + '/' :''}routes_reverseRouting.scala"
    }

    @Override
    def getScalaRoutesFileName(String packageName, String namespace) {
        return "${packageName ? packageName + '/' :''}routes_routing.scala"
    }

    @Override
    def getOtherRoutesFileNames() {
        return []
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        if (isPlay22(version)) {
            executer.expectDeprecationWarning()
        }
        return super.succeeds(tasks)
    }

    @Override
    protected ExecutionFailure fails(String... tasks) {
        if (isPlay22(version)) {
            executer.expectDeprecationWarning()
        }
        return super.fails(tasks)
    }

    def withControllerSource(TestFile file, String packageId) {
        file.createFile()
        file << """
package controllers${packageId}


import play.api._
import play.api.mvc._
import models._

object Application extends Controller {
  def index = Action {
    Ok("Your new application is ready.")
  }
}
"""
    }

    protected String newRoute(String route, String pkg) {
        return """
GET     /${route}                controllers${pkg}.Application.index()
"""
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
        failure.assertHasCause("Injected routers are only supported in Play 2.4 or newer.")
    }
}
