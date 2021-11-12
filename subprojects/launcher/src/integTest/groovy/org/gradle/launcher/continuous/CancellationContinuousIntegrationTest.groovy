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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CancellationContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        buildFile.text = "apply plugin: 'java'"

        file("src/main/java/MyClass.java") << "public class MyClass {}"
    }

    def "should cancel build when System.in contains EOT"() {
        given:
        succeeds("build")

        when:
        gradle.cancelWithEOT()

        then:
        cancelsAndExits()
    }

    def "should cancel build when System.in is closed"() {
        given:
        succeeds("build")

        when:
        gradle.stdinPipe.close()

        then:
        cancelsAndExits()
    }

    def "should cancel build when System.in contains some other characters, then closes"() {
        when:
        succeeds("build")
        stdinPipe << 'abc'

        then:
        doesntExit()

        when:
        gradle.stdinPipe.close()

        then:
        cancelsAndExits()
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    // GradleHandle.abort() is unsafe on Windows - this is a test infrastructure problem
    def "does not cancel on EOT or by closing System.in when not interactive"() {
        when:
        executer.beforeExecute {
            it.withForceInteractive(false).withStdinPipe(new PipedOutputStream() {
                @Override
                void connect(PipedInputStream snk) throws IOException {
                    super.connect(snk)
                    close()
                }
            })
        }
        killToStop = true

        then:
        succeeds "build" // tests message
        output.endsWith("...\n")

        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds()
    }

}
