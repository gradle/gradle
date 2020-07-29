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

package org.gradle.language.scala

import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.language.scala.fixtures.BadScalaLibrary
import org.gradle.language.scala.fixtures.TestScalaComponent

@UnsupportedWithInstantExecution(because = "software model")
class ScalaLanguageIntegrationTest extends AbstractJvmLanguageIntegrationTest {
    TestJvmComponent app = new TestScalaComponent()

    def "reports failure to compile bad scala sources"() {
        when:
        def badApp = new BadScalaLibrary()
        badApp.sources*.writeToDir(file("src/myLib/scala"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec)
                }
            }
        """
        then:
        fails "assemble"

        and:
        failure.assertHasDescription("Execution failed for task ':compileMyLibJarMyLibScala'.")
        failure.assertHasCause("Compilation failed")
        badApp.compilerErrors.each {
            failure.assertHasErrorOutput(it)
        }
    }
}
