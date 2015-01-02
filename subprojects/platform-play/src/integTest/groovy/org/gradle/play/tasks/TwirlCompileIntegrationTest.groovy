/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.play.integtest.fixtures.MultiPlayVersionIntegrationTest

class TwirlCompileIntegrationTest extends MultiPlayVersionIntegrationTest {

    def setup(){
        buildFile << """
        model {
            tasks {
                create("twirlCompile", TwirlCompile){ task ->
                    task.outputDirectory = file('build/twirl')
                    task.sourceDirectory = file('./app')
                    task.platform = binaries.playBinary.targetPlatform
                }
            }
        }
"""
    }

    def "can run TwirlCompile"(){
        given:
        withTwirlTemplate()
        when:
        succeeds("twirlCompile")
        then:
        file("build/twirl/views/html/index.template.scala").exists()

        when:
        succeeds("twirlCompile")
        then:
        skipped(":twirlCompile");
    }

    def "runs compiler incrementally"(){
        when:
        withTwirlTemplate("input1.scala.html")
        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
        def input1FirstCompileSnapshot = file("build/twirl/views/html/input1.template.scala").snapshot();

        when:
        withTwirlTemplate("input2.scala.html")
        and:
        succeeds("twirlCompile")
        then:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala", "input2.template.scala")
        and:
        file("build/twirl/views/html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)

        when:
        file("app/views/input2.scala.html").delete()
        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        withTwirlTemplate("input1.scala.html")
        withTwirlTemplate("input2.scala.html")
        succeeds("twirlCompile")

        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala", "input2.template.scala")
        def input1FirstCompileSnapshot = file("build/twirl/views/html/input1.template.scala").snapshot();

        when:
        file("app/views/input2.scala.html").delete()

        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
        file("build/twirl/views/html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot);
    }

    def withTwirlTemplate(String fileName = "index.scala.html") {
        def templateFile = file("app", "views", fileName)
        templateFile.createFile()
        templateFile << """@(message: String)

@main("Welcome to Play") {

    @play20.welcome(message)

}

"""
        buildFile << """
            model{
                tasks.twirlCompile{
                    source '${templateFile.toURI()}'
                }
            }"""

    }
}
