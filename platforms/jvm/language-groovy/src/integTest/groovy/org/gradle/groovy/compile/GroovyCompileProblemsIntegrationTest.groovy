/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyCompileProblemsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()

        buildFile << """\
            apply plugin: 'groovy'

            dependencies {
                implementation localGroovy()
            }
        """
    }

    def "when doing a join compilation java problems are formatted the same as a standalone compication"() {
        given:
        file("src/main/groovy/JavaThing.java") << """\
            public class JavaThing {
                public void badMethod() {
                    // The following line will cause a compilation error
                    return "Hello, World!"
                }
            }
        """
        file("src/main/groovy/GroovyBar.groovy") << "public class GroovyBar { def bar() {} }"

        when:
        fails(":compileGroovy")

        then:
        // If the joint compilation is working correctly, we should exercise the JdkJavaCompiler and we should have detailed problems events
        verifyAll(receivedProblem(0)) {
            fqid == 'compilation:java:java-compilation-error'
            contextualLabel == "';' expected"
        }

        // We also check if the error counting also works,
        // which is a separate functionality in the DiagnosticToProblemListener class next to the error reporting
        result.error.contains("1 error")

    }

}
