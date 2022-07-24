/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.scala.scaladoc

import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.scala.ScalaCompilationFixture

@TargetCoverage({ ScalaCoverage.DEFAULT})
class ScalaDocMultiVersionIntegrationTest extends MultiVersionIntegrationSpec {

    String scaladoc = ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    ScalaCompilationFixture classes = new ScalaCompilationFixture(testDirectory)

    def "can generate ScalaDoc"() {
        classes.scalaVersion = version
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc
        then:
        executedAndNotSkipped scaladoc
        file("build/docs/scaladoc/Person.html").assertExists()
        file("build/docs/scaladoc/House.html").assertExists()
        file("build/docs/scaladoc/Other.html").assertExists()
    }
}
